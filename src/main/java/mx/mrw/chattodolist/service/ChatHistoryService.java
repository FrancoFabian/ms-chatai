package mx.mrw.chattodolist.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import mx.mrw.chattodolist.api.dto.AppendChatMessageRequest;
import mx.mrw.chattodolist.api.dto.ChatMessageAttachmentInput;
import mx.mrw.chattodolist.api.dto.ChatMessageAttachmentResponse;
import mx.mrw.chattodolist.api.dto.ChatMessageResponse;
import mx.mrw.chattodolist.api.dto.ChatSessionResponse;
import mx.mrw.chattodolist.api.dto.CreateChatSessionRequest;
import mx.mrw.chattodolist.domain.ChatMessageAttachmentEntity;
import mx.mrw.chattodolist.domain.ChatMessageAttachmentRepository;
import mx.mrw.chattodolist.domain.ChatMessageEntity;
import mx.mrw.chattodolist.domain.ChatMessageRepository;
import mx.mrw.chattodolist.domain.ChatSessionEntity;
import mx.mrw.chattodolist.domain.ChatSessionRepository;
import mx.mrw.chattodolist.exception.ApiException;

@Service
public class ChatHistoryService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_SESSION_TITLE = "New Session";
    private static final Set<String> ALLOWED_SENDERS = Set.of("USER", "BOT");
    private static final Set<String> ALLOWED_ATTACHMENT_MIME = Set.of("image/png", "image/jpeg", "image/webp");
    private static final String WELCOME_MESSAGE = "Hola. Soy tu asistente de feedback. "
            + "Te dire en que ruta y seccion estas para registrar cambios con contexto. "
            + "Si necesitas mayor precision, usa etiquetas como #Clients/pagos al inicio de tu mensaje.";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageAttachmentRepository chatMessageAttachmentRepository;
    private final MediaStorageService mediaStorageService;

    public ChatHistoryService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatMessageAttachmentRepository chatMessageAttachmentRepository,
            MediaStorageService mediaStorageService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageAttachmentRepository = chatMessageAttachmentRepository;
        this.mediaStorageService = mediaStorageService;
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(String subject) {
        List<ChatSessionEntity> sessions = chatSessionRepository.findTop50BySubjectOrderByUpdatedAtDesc(subject);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<String> sessionIds = sessions.stream().map(ChatSessionEntity::getId).toList();
        List<ChatMessageEntity> messages = chatMessageRepository.findBySessionIdInOrderByCreatedAtAsc(sessionIds);
        Map<String, List<ChatMessageEntity>> messagesBySession = new LinkedHashMap<>();
        for (ChatMessageEntity message : messages) {
            messagesBySession.computeIfAbsent(message.getSessionId(), ignored -> new ArrayList<>()).add(message);
        }

        Map<String, List<ChatMessageAttachmentResponse>> attachmentsByMessage = loadAttachments(messages);
        List<ChatSessionResponse> response = new ArrayList<>(sessions.size());
        for (ChatSessionEntity session : sessions) {
            List<ChatMessageResponse> mappedMessages = mapMessages(
                    messagesBySession.getOrDefault(session.getId(), List.of()),
                    attachmentsByMessage);
            response.add(new ChatSessionResponse(
                    session.getId(),
                    session.getTitle(),
                    session.getStatus(),
                    session.getCreatedAt(),
                    session.getUpdatedAt(),
                    mappedMessages));
        }
        return response;
    }

    @Transactional
    public ChatSessionResponse createSession(String subject, CreateChatSessionRequest request) {
        String requestedId = normalizeId(request == null ? null : request.sessionId(), "SES");
        if (StringUtils.hasText(requestedId)) {
            ChatSessionEntity existingOwned = chatSessionRepository.findByIdAndSubject(requestedId, subject).orElse(null);
            if (existingOwned != null) {
                return mapSingleSession(existingOwned);
            }
        }

        String sessionId = StringUtils.hasText(requestedId) ? requestedId : generateId("SES");
        if (chatSessionRepository.findById(sessionId).isPresent()) {
            sessionId = generateId("SES");
        }

        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(sessionId);
        entity.setSubject(subject);
        entity.setTitle(resolveTitle(request == null ? null : request.title()));
        entity.setStatus(STATUS_ACTIVE);
        chatSessionRepository.saveAndFlush(entity);

        appendWelcomeMessage(sessionId);
        ChatSessionEntity persisted = chatSessionRepository.findByIdAndSubject(sessionId, subject).orElse(entity);
        return mapSingleSession(persisted);
    }

    @Transactional
    public ChatMessageResponse appendMessage(String subject, String sessionId, AppendChatMessageRequest request) {
        ChatSessionEntity session = requireOwnedSession(subject, sessionId);
        String messageId = normalizeId(request.messageId(), "MSG");
        if (!StringUtils.hasText(messageId)) {
            messageId = generateId("MSG");
        }

        ChatMessageEntity existingMessage = chatMessageRepository.findById(messageId).orElse(null);
        if (existingMessage != null && sessionId.equals(existingMessage.getSessionId())) {
            return mapExistingMessage(existingMessage);
        }
        if (existingMessage != null) {
            messageId = generateId("MSG");
        }

        String sender = normalizeSender(request.sender());
        String text = request.text().trim();
        if (!StringUtils.hasText(text)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_TEXT", "Message text is required");
        }

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(messageId);
        message.setSessionId(sessionId);
        message.setSender(sender);
        message.setText(text);
        message.setTaskId(normalizeTaskId(request.taskId()));
        chatMessageRepository.saveAndFlush(message);

        List<ChatMessageAttachmentResponse> attachmentResponses = saveMessageAttachments(messageId, request.attachments());

        session.setStatus(STATUS_ACTIVE);
        if ("USER".equals(sender) && DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            session.setTitle(deriveTitle(text));
        }
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);

        ChatMessageEntity persistedMessage = chatMessageRepository.findById(messageId).orElse(message);
        return mapExistingMessage(persistedMessage);
    }

    @Transactional
    public ChatSessionResponse clearMessages(String subject, String sessionId) {
        ChatSessionEntity session = requireOwnedSession(subject, sessionId);
        chatMessageRepository.deleteBySessionId(sessionId);
        appendWelcomeMessage(sessionId);
        session.setStatus(STATUS_ACTIVE);
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);
        return mapSingleSession(session);
    }

    private ChatSessionEntity requireOwnedSession(String subject, String sessionId) {
        return chatSessionRepository.findByIdAndSubject(sessionId, subject)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
    }

    private void appendWelcomeMessage(String sessionId) {
        ChatMessageEntity welcome = new ChatMessageEntity();
        welcome.setId(generateId("MSG"));
        welcome.setSessionId(sessionId);
        welcome.setSender("BOT");
        welcome.setText(WELCOME_MESSAGE);
        welcome.setTaskId(null);
        chatMessageRepository.save(welcome);
    }

    private ChatSessionResponse mapSingleSession(ChatSessionEntity session) {
        List<ChatMessageEntity> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        Map<String, List<ChatMessageAttachmentResponse>> attachmentsByMessage = loadAttachments(messages);
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                mapMessages(messages, attachmentsByMessage));
    }

    private ChatMessageResponse mapExistingMessage(ChatMessageEntity message) {
        Map<String, List<ChatMessageAttachmentResponse>> attachmentsByMessage = loadAttachments(List.of(message));
        return new ChatMessageResponse(
                message.getId(),
                message.getCreatedAt(),
                message.getSender(),
                message.getText(),
                message.getTaskId(),
                attachmentsByMessage.getOrDefault(message.getId(), List.of()));
    }

    private Map<String, List<ChatMessageAttachmentResponse>> loadAttachments(List<ChatMessageEntity> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        List<String> messageIds = messages.stream().map(ChatMessageEntity::getId).toList();
        List<ChatMessageAttachmentEntity> attachments = chatMessageAttachmentRepository
                .findByMessageIdInOrderByCreatedAtAsc(messageIds);
        Map<String, List<ChatMessageAttachmentResponse>> byMessage = new HashMap<>();
        for (ChatMessageAttachmentEntity attachment : attachments) {
            byMessage.computeIfAbsent(attachment.getMessageId(), ignored -> new ArrayList<>())
                    .add(new ChatMessageAttachmentResponse(
                            attachment.getMediaPath(),
                            attachment.getMimeType(),
                            attachment.getSizeBytes(),
                            mediaStorageService.publicUrlFor(attachment.getMediaPath())));
        }
        return byMessage;
    }

    private List<ChatMessageResponse> mapMessages(
            List<ChatMessageEntity> messages,
            Map<String, List<ChatMessageAttachmentResponse>> attachmentsByMessage) {
        List<ChatMessageResponse> mapped = new ArrayList<>(messages.size());
        for (ChatMessageEntity message : messages) {
            mapped.add(new ChatMessageResponse(
                    message.getId(),
                    message.getCreatedAt(),
                    message.getSender(),
                    message.getText(),
                    message.getTaskId(),
                    attachmentsByMessage.getOrDefault(message.getId(), List.of())));
        }
        return mapped;
    }

    private List<ChatMessageAttachmentResponse> saveMessageAttachments(
            String messageId,
            List<ChatMessageAttachmentInput> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<ChatMessageAttachmentResponse> responses = new ArrayList<>();
        for (ChatMessageAttachmentInput attachment : attachments) {
            validateAttachment(attachment);
            ChatMessageAttachmentEntity entity = new ChatMessageAttachmentEntity();
            entity.setMessageId(messageId);
            entity.setMediaPath(attachment.mediaPath().replace('\\', '/'));
            entity.setMimeType(attachment.mimeType().toLowerCase(Locale.ROOT));
            entity.setSizeBytes(attachment.sizeBytes());
            chatMessageAttachmentRepository.save(entity);
            responses.add(new ChatMessageAttachmentResponse(
                    entity.getMediaPath(),
                    entity.getMimeType(),
                    entity.getSizeBytes(),
                    mediaStorageService.publicUrlFor(entity.getMediaPath())));
        }
        return responses;
    }

    private void validateAttachment(ChatMessageAttachmentInput attachment) {
        if (attachment == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT", "Attachment is required");
        }
        String mediaPath = attachment.mediaPath() == null ? "" : attachment.mediaPath().trim();
        String mimeType = attachment.mimeType() == null ? "" : attachment.mimeType().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(mediaPath) || mediaPath.startsWith("/") || mediaPath.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_PATH", "Attachment mediaPath is invalid");
        }
        if (!ALLOWED_ATTACHMENT_MIME.contains(mimeType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_MIME", "Attachment mimeType is not allowed");
        }
        if (attachment.sizeBytes() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_SIZE", "Attachment size must be positive");
        }
    }

    private String resolveTitle(String requestedTitle) {
        if (!StringUtils.hasText(requestedTitle)) {
            return DEFAULT_SESSION_TITLE;
        }
        return requestedTitle.trim();
    }

    private String normalizeSender(String sender) {
        String normalized = sender == null ? "" : sender.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SENDERS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_SENDER", "Sender must be USER or BOT");
        }
        return normalized;
    }

    private String normalizeTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        return taskId.trim();
    }

    private String normalizeId(String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.trim();
        if (!candidate.matches("[A-Za-z0-9_-]{3,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDENTIFIER", "Invalid " + prefix + " identifier");
        }
        return candidate;
    }

    private String generateId(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return prefix + "-" + random;
    }

    private String deriveTitle(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.startsWith("#")) {
            int firstSpace = normalized.indexOf(' ');
            if (firstSpace > 0 && firstSpace + 1 < normalized.length()) {
                normalized = normalized.substring(firstSpace + 1).trim();
            }
        }
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SESSION_TITLE;
        }
        String oneLine = normalized.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= 72) {
            return oneLine;
        }
        return oneLine.substring(0, 69).trim() + "...";
    }
}
