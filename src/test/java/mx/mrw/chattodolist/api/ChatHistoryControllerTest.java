package mx.mrw.chattodolist.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import mx.mrw.chattodolist.api.dto.ChatMessageAttachmentResponse;
import mx.mrw.chattodolist.api.dto.ChatMessageResponse;
import mx.mrw.chattodolist.api.dto.ChatSessionResponse;
import mx.mrw.chattodolist.exception.GlobalExceptionHandler;
import mx.mrw.chattodolist.service.ChatHistoryService;

class ChatHistoryControllerTest {

    private MockMvc mockMvc;
    private ChatHistoryService chatHistoryService;

    @BeforeEach
    void setUp() {
        chatHistoryService = Mockito.mock(ChatHistoryService.class);
        ChatHistoryController controller = new ChatHistoryController(chatHistoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listSessionsShouldReturnJsonShape() throws Exception {
        ChatMessageResponse message = new ChatMessageResponse(
                "MSG-001",
                Instant.parse("2026-02-22T10:00:00Z"),
                "USER",
                "Hola",
                "TASK-1",
                List.of(new ChatMessageAttachmentResponse("u/2026/02/abc.png", "image/png", 1024, "http://localhost/media/u/2026/02/abc.png")));
        ChatSessionResponse session = new ChatSessionResponse(
                "SES-001",
                "Sesion 1",
                "ACTIVE",
                Instant.parse("2026-02-22T09:00:00Z"),
                Instant.parse("2026-02-22T10:00:00Z"),
                List.of(message));

        when(chatHistoryService.listSessions(anyString())).thenReturn(List.of(session));

        mockMvc.perform(get("/api/feedback-chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].id").value("SES-001"))
                .andExpect(jsonPath("$.sessions[0].messages[0].id").value("MSG-001"))
                .andExpect(jsonPath("$.sessions[0].messages[0].attachments[0].mediaPath").value("u/2026/02/abc.png"));
    }

    @Test
    void createSessionShouldReturnSession() throws Exception {
        ChatSessionResponse session = new ChatSessionResponse(
                "SES-NEW",
                "New Session",
                "ACTIVE",
                Instant.parse("2026-02-22T09:00:00Z"),
                Instant.parse("2026-02-22T09:00:00Z"),
                List.of());
        when(chatHistoryService.createSession(anyString(), any())).thenReturn(session);

        mockMvc.perform(post("/api/feedback-chat/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"SES-NEW\",\"title\":\"New Session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("SES-NEW"))
                .andExpect(jsonPath("$.title").value("New Session"));
    }

    @Test
    void appendMessageShouldReturnMessage() throws Exception {
        ChatMessageResponse message = new ChatMessageResponse(
                "MSG-NEW",
                Instant.parse("2026-02-22T10:00:00Z"),
                "USER",
                "Texto",
                null,
                List.of());
        when(chatHistoryService.appendMessage(anyString(), anyString(), any())).thenReturn(message);

        mockMvc.perform(post("/api/feedback-chat/sessions/SES-NEW/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":\"MSG-NEW\",\"sender\":\"USER\",\"text\":\"Texto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("MSG-NEW"))
                .andExpect(jsonPath("$.sender").value("USER"));
    }

    @Test
    void clearMessagesShouldReturnSession() throws Exception {
        ChatSessionResponse session = new ChatSessionResponse(
                "SES-NEW",
                "New Session",
                "ACTIVE",
                Instant.parse("2026-02-22T09:00:00Z"),
                Instant.parse("2026-02-22T10:00:00Z"),
                List.of());
        when(chatHistoryService.clearMessages(anyString(), anyString())).thenReturn(session);

        mockMvc.perform(delete("/api/feedback-chat/sessions/SES-NEW/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("SES-NEW"));
    }
}
