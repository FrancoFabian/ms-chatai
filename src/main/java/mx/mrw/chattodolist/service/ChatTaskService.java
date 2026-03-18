package mx.mrw.chattodolist.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.openaisdk.OpenAiSdkChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import mx.mrw.chattodolist.api.dto.FeedbackChatRequest;
import mx.mrw.chattodolist.api.dto.ImproveMessageRequest;
import mx.mrw.chattodolist.api.dto.TaskAttachmentInput;
import mx.mrw.chattodolist.domain.TaskAttachmentEntity;
import mx.mrw.chattodolist.domain.TaskAttachmentRepository;
import mx.mrw.chattodolist.config.AppAiProperties;
import mx.mrw.chattodolist.config.AppHttpProperties;
import mx.mrw.chattodolist.config.AppLimitsProperties;
import mx.mrw.chattodolist.domain.TaskEntity;
import mx.mrw.chattodolist.domain.TaskRepository;
import mx.mrw.chattodolist.exception.ApiException;

@Service
public class ChatTaskService {

    private static final Logger logger = LoggerFactory.getLogger(ChatTaskService.class);
    private static final String PROVIDER = "openai";
    private static final String OPENAI_API_OPERATION = "chat.completions.create";
    private static final Set<String> ALLOWED_ATTACHMENT_MIME = Set.of("image/png", "image/jpeg", "image/webp");

    private final ChatClient chatClient;
    private final ObjectProvider<OpenAiSdkChatModel> openAiSdkChatModelProvider;
    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ObjectMapper objectMapper;
    private final AppLimitsProperties limitsProperties;
    private final AppAiProperties aiProperties;
    private final AppHttpProperties httpProperties;
    private final AiTelemetryService aiTelemetryService;
    private final AiInputNormalizer inputNormalizer;
    private final ImproveMessageCache improveMessageCache;
    private final AiPromptGuard aiPromptGuard;

    private final String openAiApiKey;
    private final String defaultModel;
    private final double defaultTemperature;

    public ChatTaskService(
            ChatClient.Builder chatClientBuilder,
            ObjectProvider<OpenAiSdkChatModel> openAiSdkChatModelProvider,
            TaskRepository taskRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            ObjectMapper objectMapper,
            AppLimitsProperties limitsProperties,
            AppAiProperties aiProperties,
            AppHttpProperties httpProperties,
            AiTelemetryService aiTelemetryService,
            AiInputNormalizer inputNormalizer,
            AiPromptGuard aiPromptGuard,
            ImproveMessageCache improveMessageCache,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${app.ai.default-temperature:${OPENAI_TEMPERATURE:0.2}}") double defaultTemperature) {
        this.chatClient = chatClientBuilder.build();
        this.openAiSdkChatModelProvider = openAiSdkChatModelProvider;
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.objectMapper = objectMapper;
        this.limitsProperties = limitsProperties;
        this.aiProperties = aiProperties;
        this.httpProperties = httpProperties;
        this.aiTelemetryService = aiTelemetryService;
        this.inputNormalizer = inputNormalizer;
        this.aiPromptGuard = aiPromptGuard;
        this.improveMessageCache = improveMessageCache;
        this.openAiApiKey = openAiApiKey;
        this.defaultModel = model;
        this.defaultTemperature = defaultTemperature;
    }

    public ChatTaskResult process(FeedbackChatRequest request, String subject, String requestId) {
        validateLimits(request);
        ensureAiConfigured();

        String aiSafeMessage = aiPromptGuard.sanitizeForAi(request.message());
        if (!StringUtils.hasText(aiSafeMessage)) {
            aiSafeMessage = "El usuario compartio referencias visuales. Solicita una descripcion textual breve.";
        }
        NormalizedInput normalizedMessage = inputNormalizer.normalizeMessage(AiFlow.FEEDBACK_CHAT, aiSafeMessage);
        String normalizedRoute = inputNormalizer.normalizeContextValue(request.route());
        String normalizedSectionTag = inputNormalizer.normalizeContextValue(request.sectionTag());
        String normalizedRole = inputNormalizer.normalizeContextValue(request.role());
        String normalizedRoleTag = inputNormalizer.normalizeContextValue(request.roleTag());
        String normalizedTaskId = inputNormalizer.normalizeContextValue(request.taskId());
        String normalizedTaskType = inputNormalizer.normalizeContextValue(request.taskType());
        String normalizedPriority = inputNormalizer.normalizeContextValue(request.priority());
        String normalizedUser = inputNormalizer.normalizeContextValue(request.userName());

        String selectedModel = resolveModel(request.modelPreference());
        ModelCallResult modelCall = callModel(
                AiFlow.FEEDBACK_CHAT,
                systemPrompt(),
                userPrompt(
                        normalizedUser,
                        normalizedRole,
                        normalizedRoleTag,
                        normalizedRoute,
                        normalizedSectionTag,
                        normalizedTaskId,
                        normalizedTaskType,
                        normalizedPriority,
                        request.isGeneralMode(),
                        contextRisk(normalizedRoute, normalizedSectionTag, normalizedMessage.text(), normalizedTaskType),
                        normalizedMessage.text()),
                selectedModel);

        ParseAttempt<ParsedAssistantResponse> parseAttempt = parseAssistantResponse(modelCall.content());
        boolean truncated = isTruncated(modelCall, parseAttempt.validJson(), parseAttempt.likelyJsonCut());
        String fallbackMode = "NONE";
        ParsedAssistantResponse parsed = parseAttempt.parsed();
        if (parsed == null) {
            if (truncated || parseAttempt.likelyJsonCut()) {
                parsed = fallbackFeedbackReply();
                fallbackMode = "FLOW1_TRUNCATED_JSON_FALLBACK";
            }
            else {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_INVALID_RESPONSE", "AI response is not valid JSON");
            }
        }
        persistTask(subject, request, parsed.reply(), parsed.questions(), modelCall.model());

        UsageMetrics usage = aiTelemetryService.track(new AiUsageTrackInput(
                requestId,
                subject,
                AiFlow.FEEDBACK_CHAT.flowName(),
                AiFlow.FEEDBACK_CHAT.endpoint(),
                AiUsageEventType.CHAT_REPLY,
                request.taskId(),
                PROVIDER,
                modelCall.model(),
                request.route(),
                modelCall.promptVersion(),
                normalizedMessage.normalizationMode(),
                modelCall.maxTokensApplied(),
                modelCall.inputChars(),
                modelCall.outputChars(),
                truncated,
                fallbackMode,
                false,
                false,
                modelCall.temperatureApplied(),
                modelCall.usage()));

        return new ChatTaskResult(parsed.reply(), PROVIDER, modelCall.model(), usage, parsed.questions());
    }

    public ImproveMessageResult improveMessage(ImproveMessageRequest request, String subject, String requestId) {
        validateImproveLimits(request);
        ensureAiConfigured();

        String aiSafeMessage = aiPromptGuard.sanitizeForAi(request.message());
        if (!StringUtils.hasText(aiSafeMessage)) {
            aiSafeMessage = "Mejorar redaccion con base en descripcion textual, sin analizar imagenes adjuntas.";
        }
        NormalizedInput normalizedMessage = inputNormalizer.normalizeMessage(AiFlow.IMPROVE_MESSAGE, aiSafeMessage);
        String normalizedRoute = inputNormalizer.normalizeContextValue(request.route());
        String normalizedSectionTag = inputNormalizer.normalizeContextValue(request.sectionTag());
        String normalizedRole = inputNormalizer.normalizeContextValue(request.role());
        String normalizedRoleTag = inputNormalizer.normalizeContextValue(request.roleTag());
        String normalizedTaskType = inputNormalizer.normalizeContextValue(request.taskType());
        String normalizedPriority = inputNormalizer.normalizeContextValue(request.priority());
        String normalizedUser = inputNormalizer.normalizeContextValue(request.userName());

        String selectedModel = resolveModel(request.modelPreference());
        int maxTokensApplied = resolveMaxTokens(AiFlow.IMPROVE_MESSAGE);
        String promptVersion = AiFlow.IMPROVE_MESSAGE.promptVersion();
        String cacheKey = improveMessageCache.hashKey(
                normalizedMessage.text(),
                normalizedRoleTag,
                normalizedRoute,
                normalizedSectionTag,
                selectedModel,
                promptVersion,
                normalizedMessage.normalizationMode(),
                resolveEffectiveTemperature(selectedModel));
        ImproveMessageCache.CacheResult cacheResult = improveMessageCache.get(cacheKey);
        if (cacheResult.hit()) {
            UsageMetrics usageFromCache = aiTelemetryService.track(new AiUsageTrackInput(
                    requestId,
                    subject,
                    AiFlow.IMPROVE_MESSAGE.flowName(),
                    AiFlow.IMPROVE_MESSAGE.endpoint(),
                    AiUsageEventType.MESSAGE_IMPROVEMENT,
                    null,
                    PROVIDER,
                    selectedModel,
                    request.route(),
                    promptVersion,
                    normalizedMessage.normalizationMode(),
                    maxTokensApplied,
                    0,
                    cacheResult.value().length(),
                    false,
                    "NONE",
                    true,
                    false,
                    resolveEffectiveTemperature(selectedModel),
                    null));
            return new ImproveMessageResult(cacheResult.value(), PROVIDER, selectedModel, usageFromCache);
        }

        ModelCallResult modelCall = callModel(
                AiFlow.IMPROVE_MESSAGE,
                improveSystemPrompt(),
                improveUserPrompt(
                        normalizedUser,
                        normalizedRole,
                        normalizedRoleTag,
                        normalizedRoute,
                        normalizedSectionTag,
                        normalizedTaskType,
                        normalizedPriority,
                        request.isGeneralMode(),
                        contextRisk(normalizedRoute, normalizedSectionTag, normalizedMessage.text(), normalizedTaskType),
                        normalizedMessage.text()),
                selectedModel);

        ParseAttempt<String> parseAttempt = parseImprovedMessage(modelCall.content());
        boolean truncated = isTruncated(modelCall, parseAttempt.validJson(), parseAttempt.likelyJsonCut());
        String fallbackMode = "NONE";
        String improvedMessage = parseAttempt.parsed();
        if (!StringUtils.hasText(improvedMessage)) {
            if (truncated || parseAttempt.likelyJsonCut()) {
                improvedMessage = normalizedMessage.text();
                fallbackMode = "FLOW2_ORIGINAL_NORMALIZED_FALLBACK";
            }
            else {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_INVALID_RESPONSE", "AI response is not valid JSON");
            }
        }

        if ("NONE".equals(fallbackMode)) {
            improveMessageCache.put(cacheKey, improvedMessage);
        }

        UsageMetrics usage = aiTelemetryService.track(new AiUsageTrackInput(
                requestId,
                subject,
                AiFlow.IMPROVE_MESSAGE.flowName(),
                AiFlow.IMPROVE_MESSAGE.endpoint(),
                AiUsageEventType.MESSAGE_IMPROVEMENT,
                null,
                PROVIDER,
                modelCall.model(),
                request.route(),
                modelCall.promptVersion(),
                normalizedMessage.normalizationMode(),
                modelCall.maxTokensApplied(),
                modelCall.inputChars(),
                modelCall.outputChars(),
                truncated,
                fallbackMode,
                false,
                false,
                modelCall.temperatureApplied(),
                modelCall.usage()));

        return new ImproveMessageResult(improvedMessage, PROVIDER, modelCall.model(), usage);
    }

    public UsageSummaryData usageSummary(String subject) {
        return aiTelemetryService.summaryForSubject(subject);
    }

    private void ensureAiConfigured() {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI provider is not configured");
        }
    }

    private void validateLimits(FeedbackChatRequest request) {
        if (request.message().length() > limitsProperties.getMaxUserMessageChars()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "Message exceeds allowed size");
        }
        int contextLength = request.route().length() + request.sectionTag().length() + request.taskId().length();
        if (contextLength > limitsProperties.getMaxContextChars()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTEXT_TOO_LONG", "Context exceeds allowed size");
        }
    }

    private void validateImproveLimits(ImproveMessageRequest request) {
        if (request.message().length() > limitsProperties.getMaxUserMessageChars()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "Message exceeds allowed size");
        }
        int contextLength = request.route().length() + request.sectionTag().length();
        if (contextLength > limitsProperties.getMaxContextChars()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTEXT_TOO_LONG", "Context exceeds allowed size");
        }
    }

    private String systemPrompt() {
        return """
                Devuelve SOLO JSON valido, sin markdown y sin texto extra:
                {"reply":"<respuesta breve y accionable>","questions":["<pregunta especifica>", "..."]}.
                No inventes datos, pantallas, reglas ni requisitos fuera del contexto entregado.
                Si falta contexto para ejecutar con precision, devuelve hasta 3 preguntas en "questions".
                Si el contexto es suficiente, devuelve "questions": [].
                reply debe ser corto, imperativo y sin relleno.
                """;
    }

    private String improveSystemPrompt() {
        return """
                Devuelve SOLO JSON valido, sin markdown y sin texto extra:
                {"improvedMessage":"<texto mejorado>"}.
                Conserva intencion original y no inventes datos, bugs, requisitos ni pantallas.
                Conserva rutas, tags, IDs y numeros tecnicos existentes.
                Si el mensaje ya es claro, mejora solo redaccion minima.
                """;
    }

    private String userPrompt(
            String userName,
            String role,
            String roleTag,
            String route,
            String sectionTag,
            String taskId,
            String taskType,
            String priority,
            boolean isGeneralMode,
            String contextRisk,
            String message) {
        String safeUserName = StringUtils.hasText(userName) ? userName : "Sin nombre";
        return """
                ctx{route=%s; sectionTag=%s; role=%s; roleTag=%s; taskId=%s; taskType=%s; priority=%s; generalMode=%s; user=%s; contextRisk=%s}
                message:
                %s
                """.formatted(
                route,
                sectionTag,
                role,
                roleTag,
                taskId,
                taskType,
                priority,
                isGeneralMode,
                safeUserName,
                contextRisk,
                message);
    }

    private String improveUserPrompt(
            String userName,
            String role,
            String roleTag,
            String route,
            String sectionTag,
            String taskType,
            String priority,
            boolean isGeneralMode,
            String contextRisk,
            String message) {
        String safeUserName = StringUtils.hasText(userName) ? userName : "Sin nombre";
        return """
                ctx{route=%s; sectionTag=%s; role=%s; roleTag=%s; taskType=%s; priority=%s; generalMode=%s; user=%s; contextRisk=%s}
                message:
                %s
                """.formatted(
                route,
                sectionTag,
                role,
                roleTag,
                taskType,
                priority,
                isGeneralMode,
                safeUserName,
                contextRisk,
                message);
    }

    private String contextRisk(String route, String sectionTag, String message, String taskType) {
        int signalScore = 0;
        if (route.length() >= 4) {
            signalScore++;
        }
        if (sectionTag.length() >= 3) {
            signalScore++;
        }
        if (message.trim().length() >= 30) {
            signalScore++;
        }
        if (taskType.length() >= 3) {
            signalScore++;
        }

        if (signalScore >= 4) {
            return "LOW";
        }
        if (signalScore >= 2) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private ParseAttempt<ParsedAssistantResponse> parseAssistantResponse(String rawModelResponse) {
        if (!StringUtils.hasText(rawModelResponse)) {
            return ParseAttempt.invalid(true);
        }

        String jsonPayload = extractJsonPayload(rawModelResponse.trim());
        boolean likelyJsonCut = isLikelyJsonCut(rawModelResponse);

        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String reply = root.path("reply").asText("").trim();
            List<String> questions = new ArrayList<>();
            JsonNode questionsNode = root.path("questions");
            if (questionsNode.isArray()) {
                for (JsonNode questionNode : questionsNode) {
                    String question = questionNode.asText("").trim();
                    if (!question.isEmpty()) {
                        questions.add(question);
                    }
                }
            }

            if (!StringUtils.hasText(reply) && !questions.isEmpty()) {
                reply = String.join("\n", questions);
            }
            if (!StringUtils.hasText(reply)) {
                return ParseAttempt.invalid(likelyJsonCut);
            }

            return ParseAttempt.valid(new ParsedAssistantResponse(reply, questions), likelyJsonCut);
        }
        catch (Exception exception) {
            return ParseAttempt.invalid(likelyJsonCut);
        }
    }

    private ParseAttempt<String> parseImprovedMessage(String rawModelResponse) {
        if (!StringUtils.hasText(rawModelResponse)) {
            return ParseAttempt.invalid(true);
        }
        String jsonPayload = extractJsonPayload(rawModelResponse.trim());
        boolean likelyJsonCut = isLikelyJsonCut(rawModelResponse);
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String improvedMessage = root.path("improvedMessage").asText("").trim();
            if (!StringUtils.hasText(improvedMessage)) {
                improvedMessage = root.path("reply").asText("").trim();
            }
            if (!StringUtils.hasText(improvedMessage)) {
                return ParseAttempt.invalid(likelyJsonCut);
            }
            return ParseAttempt.valid(improvedMessage, likelyJsonCut);
        }
        catch (Exception exception) {
            return ParseAttempt.invalid(likelyJsonCut);
        }
    }

    private String extractJsonPayload(String raw) {
        int firstBrace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return raw.substring(firstBrace, lastBrace + 1);
        }
        return raw;
    }

    private boolean isTruncated(ModelCallResult modelCall, boolean validJson, boolean likelyJsonCut) {
        if (StringUtils.hasText(modelCall.finishReason())
                && "length".equalsIgnoreCase(modelCall.finishReason().trim())) {
            return true;
        }

        int completionTokens = modelCall.usage() != null && modelCall.usage().getCompletionTokens() != null
                ? modelCall.usage().getCompletionTokens()
                : 0;
        boolean closeToCap = completionTokens >= Math.ceil(modelCall.maxTokensApplied() * 0.98d);
        if (closeToCap && (likelyJsonCut || !validJson)) {
            return true;
        }
        return !validJson && likelyJsonCut;
    }

    private boolean isLikelyJsonCut(String rawModelResponse) {
        if (!StringUtils.hasText(rawModelResponse)) {
            return true;
        }
        String trimmed = rawModelResponse.trim();
        if (!trimmed.contains("{")) {
            return false;
        }
        if (!trimmed.endsWith("}")) {
            return true;
        }
        if (countChar(trimmed, '{') != countChar(trimmed, '}')) {
            return true;
        }
        return hasUnbalancedQuotes(trimmed);
    }

    private int countChar(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private boolean hasUnbalancedQuotes(String value) {
        boolean openQuote = false;
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                openQuote = !openQuote;
            }
        }
        return openQuote;
    }

    private ParsedAssistantResponse fallbackFeedbackReply() {
        return new ParsedAssistantResponse(
                "Comparte el flujo exacto y el resultado esperado para convertirlo en accion implementable.",
                List.of(
                        "¿En que pantalla y seccion ocurre exactamente?",
                        "¿Que comportamiento esperabas y que ocurrio realmente?"));
    }

    @Transactional
    private void persistTask(String subject, FeedbackChatRequest request, String reply, List<String> questions, String modelUsed) {
        TaskEntity task = taskRepository.findByTaskId(request.taskId()).orElseGet(TaskEntity::new);
        task.setTaskId(request.taskId());
        task.setSubject(subject);
        task.setTitle(deriveTaskTitle(request.message()));
        task.setRoute(request.route());
        task.setSectionTag(request.sectionTag());
        task.setRole(request.role());
        task.setRoleTag(request.roleTag());
        task.setTaskType(request.taskType());
        task.setPriority(request.priority());
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus("OPEN");
        }
        task.setUserName(request.userName());
        task.setGeneralMode(request.isGeneralMode());
        task.setUserMessage(request.message());
        task.setAssistantReply(questions.isEmpty() ? reply : reply + "\n" + String.join("\n", questions));
        task.setAiProvider(PROVIDER);
        task.setAiModel(modelUsed);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        taskAttachmentRepository.deleteByTaskId(request.taskId());
        if (request.attachments() == null || request.attachments().isEmpty()) {
            return;
        }

        for (TaskAttachmentInput attachment : request.attachments()) {
            validateAttachment(attachment);
            TaskAttachmentEntity entity = new TaskAttachmentEntity();
            entity.setTaskId(request.taskId());
            entity.setMediaPath(attachment.mediaPath().replace('\\', '/'));
            entity.setMimeType(attachment.mime().toLowerCase(Locale.ROOT));
            entity.setSizeBytes(attachment.size());
            taskAttachmentRepository.save(entity);
        }
    }

    private String deriveTaskTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return "Untitled task";
        }
        String trimmed = message.trim();
        if (trimmed.length() <= 60) {
            return trimmed;
        }
        int split = trimmed.substring(0, 60).lastIndexOf(' ');
        if (split > 30) {
            return trimmed.substring(0, split) + "...";
        }
        return trimmed.substring(0, 60) + "...";
    }

    private void validateAttachment(TaskAttachmentInput attachment) {
        if (attachment == null) {
            return;
        }
        String mediaPath = attachment.mediaPath() == null ? "" : attachment.mediaPath().trim();
        String mime = attachment.mime() == null ? "" : attachment.mime().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(mediaPath) || mediaPath.contains("..") || mediaPath.startsWith("/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_PATH", "Attachment mediaPath is invalid");
        }
        if (!ALLOWED_ATTACHMENT_MIME.contains(mime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_MIME", "Attachment mime is not allowed");
        }
        if (attachment.size() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_SIZE", "Attachment size must be positive");
        }
    }

    private ModelCallResult callModel(AiFlow flow, String systemPrompt, String userPrompt, String selectedModel) {
        int maxTokens = resolveMaxTokens(flow);
        OpenAiSdkChatOptions requestOptions = buildRequestOptions(selectedModel, maxTokens);
        OpenAiSdkChatOptions defaultOptions = resolveDefaultOptions();
        OpenAiSdkChatOptions mergedOptions = mergeOptions(defaultOptions, requestOptions);
        double appliedTemperature = resolveEffectiveTemperature(selectedModel);

        logger.info(
                "AI runtime request flow={} provider={} api={} baseUrl={} model={} defaultOptions={} requestOptions={} mergedOptions={}",
                flow.flowName(),
                PROVIDER,
                OPENAI_API_OPERATION,
                mergedOptions.getBaseUrl(),
                selectedModel,
                summarizeOptions(defaultOptions),
                summarizeOptions(requestOptions),
                summarizeOptions(mergedOptions));

        ResponseEntity<ChatResponse, String> responseEntity = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .options(requestOptions)
            .call()
            .responseEntity(String.class);

        ChatResponse chatResponse = responseEntity.response();
        Usage usage = chatResponse != null && chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
        String resolvedModel = selectedModel;
        if (chatResponse != null && chatResponse.getMetadata() != null && StringUtils.hasText(chatResponse.getMetadata().getModel())) {
            resolvedModel = chatResponse.getMetadata().getModel();
        }
        String finishReason = null;
        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getMetadata() != null
                && StringUtils.hasText(chatResponse.getResult().getMetadata().getFinishReason())) {
            finishReason = chatResponse.getResult().getMetadata().getFinishReason().toLowerCase(Locale.ROOT);
        }
        String content = responseEntity.entity() == null ? "" : responseEntity.entity();
        int inputChars = systemPrompt.length() + userPrompt.length();
        return new ModelCallResult(
                content,
                resolvedModel,
                usage,
                finishReason,
                maxTokens,
                appliedTemperature,
                inputChars,
                content.length(),
                flow.promptVersion());
    }

    private OpenAiSdkChatOptions buildRequestOptions(String selectedModel, int maxTokens) {
        OpenAiSdkChatOptions.Builder builder = OpenAiSdkChatOptions.builder()
                .model(selectedModel)
                .timeout(Duration.ofMillis(httpProperties.getReadTimeoutMs()))
                .responseFormat(OpenAiSdkChatModel.ResponseFormat.builder()
                        .type(OpenAiSdkChatModel.ResponseFormat.Type.JSON_OBJECT)
                        .build());

        if (usesMaxCompletionTokens(selectedModel)) {
            builder.maxCompletionTokens(maxTokens);
        }
        else {
            builder.maxTokens(maxTokens);
        }

        if (supportsCustomTemperature(selectedModel)) {
            builder.temperature(defaultTemperature);
        }

        return builder.build();
    }

    private OpenAiSdkChatOptions resolveDefaultOptions() {
        OpenAiSdkChatModel chatModel = openAiSdkChatModelProvider.getIfAvailable();
        if (chatModel == null || chatModel.getOptions() == null) {
            return null;
        }
        return chatModel.getOptions().copy();
    }

    private OpenAiSdkChatOptions mergeOptions(OpenAiSdkChatOptions defaultOptions, OpenAiSdkChatOptions requestOptions) {
        if (defaultOptions == null) {
            return requestOptions;
        }
        return OpenAiSdkChatOptions.builder()
                .from(defaultOptions)
                .merge(requestOptions)
                .build();
    }

    private boolean usesMaxCompletionTokens(String model) {
        return isGpt5Family(model);
    }

    private boolean supportsCustomTemperature(String model) {
        return !isGpt5Family(model);
    }

    private double resolveEffectiveTemperature(String model) {
        return supportsCustomTemperature(model) ? defaultTemperature : 1.0d;
    }

    private boolean isGpt5Family(String model) {
        return StringUtils.hasText(model) && model.toLowerCase(Locale.ROOT).startsWith("gpt-5");
    }

    private String summarizeOptions(OpenAiSdkChatOptions options) {
        if (options == null) {
            return "null";
        }
        return "model=%s, baseUrl=%s, timeout=%s, temperature=%s, maxTokens=%s, maxCompletionTokens=%s, topP=%s, reasoningEffort=%s, verbosity=%s, responseFormat=%s"
                .formatted(
                        options.getModel(),
                        options.getBaseUrl(),
                        options.getTimeout(),
                        options.getTemperature(),
                        options.getMaxTokens(),
                        options.getMaxCompletionTokens(),
                        options.getTopP(),
                        options.getReasoningEffort(),
                        options.getVerbosity(),
                        options.getResponseFormat());
    }

    private String resolveModel(String modelPreference) {
        if (!StringUtils.hasText(modelPreference) || "default".equalsIgnoreCase(modelPreference)) {
            return defaultModel;
        }
        if ("gpt-5-mini".equalsIgnoreCase(modelPreference)) {
            return "gpt-5-mini";
        }
        return defaultModel;
    }

    private int resolveMaxTokens(AiFlow flow) {
        int configured = flow == AiFlow.FEEDBACK_CHAT
                ? aiProperties.getFlow().getFeedbackChat().getMaxTokens()
                : aiProperties.getFlow().getImproveMessage().getMaxTokens();
        return Math.max(32, configured);
    }

    private record ParseAttempt<T>(T parsed, boolean validJson, boolean likelyJsonCut) {
        static <T> ParseAttempt<T> valid(T parsed, boolean likelyJsonCut) {
            return new ParseAttempt<>(parsed, true, likelyJsonCut);
        }

        static <T> ParseAttempt<T> invalid(boolean likelyJsonCut) {
            return new ParseAttempt<>(null, false, likelyJsonCut);
        }
    }

    private record ParsedAssistantResponse(String reply, List<String> questions) {
    }

    private record ModelCallResult(
            String content,
            String model,
            Usage usage,
            String finishReason,
            int maxTokensApplied,
            double temperatureApplied,
            int inputChars,
            int outputChars,
            String promptVersion) {
    }
}
