package mx.mrw.chattodolist.api;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mx.mrw.chattodolist.api.dto.AppendChatMessageRequest;
import mx.mrw.chattodolist.api.dto.ChatMessageResponse;
import mx.mrw.chattodolist.api.dto.ChatSessionResponse;
import mx.mrw.chattodolist.api.dto.ChatSessionsResponse;
import mx.mrw.chattodolist.api.dto.CreateChatSessionRequest;
import mx.mrw.chattodolist.security.AuthContext;
import mx.mrw.chattodolist.service.ChatHistoryService;
import mx.mrw.chattodolist.support.RequestContext;

@Validated
@RestController
@RequestMapping("/api/feedback-chat/sessions")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping
    public ChatSessionsResponse listSessions(HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        List<ChatSessionResponse> sessions = chatHistoryService.listSessions(subject);
        return new ChatSessionsResponse(requestId(httpServletRequest), sessions);
    }

    @PostMapping
    public ChatSessionResponse createSession(
            @Valid @RequestBody(required = false) CreateChatSessionRequest request,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        CreateChatSessionRequest payload = request == null ? new CreateChatSessionRequest(null, null) : request;
        return chatHistoryService.createSession(subject, payload);
    }

    @PostMapping("/{sessionId}/messages")
    public ChatMessageResponse appendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody AppendChatMessageRequest request,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        return chatHistoryService.appendMessage(subject, sessionId, request);
    }

    @DeleteMapping("/{sessionId}/messages")
    public ChatSessionResponse clearMessages(
            @PathVariable String sessionId,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        return chatHistoryService.clearMessages(subject, sessionId);
    }

    private String requestId(HttpServletRequest httpServletRequest) {
        Object value = httpServletRequest.getAttribute(RequestContext.REQUEST_ID);
        return value == null ? null : value.toString();
    }

    private String subject(HttpServletRequest httpServletRequest) {
        AuthContext authContext = (AuthContext) httpServletRequest.getAttribute(RequestContext.AUTH_CONTEXT);
        return authContext == null ? "unknown" : authContext.subject();
    }
}
