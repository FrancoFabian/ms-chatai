package mx.mrw.chattodolist.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.mrw.chattodolist.exception.GlobalExceptionHandler;
import mx.mrw.chattodolist.service.ChatTaskResult;
import mx.mrw.chattodolist.service.ChatTaskService;
import mx.mrw.chattodolist.service.UsageMetrics;
import mx.mrw.chattodolist.support.RequestContext;

class FeedbackChatControllerTest {

    private MockMvc mockMvc;
    private ChatTaskService chatTaskService;

    @BeforeEach
    void setUp() {
        chatTaskService = Mockito.mock(ChatTaskService.class);
        FeedbackChatController controller = new FeedbackChatController(chatTaskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createFeedbackTaskShouldReturnExpectedJsonShape() throws Exception {
        when(chatTaskService.process(any(), anyString(), anyString()))
            .thenReturn(new ChatTaskResult(
                    "Accion aplicada",
                    "openai",
                    "gpt-4o-mini",
                    new UsageMetrics(120, 45, 10, 165, new java.math.BigDecimal("0.00012345")),
                    List.of("Puedes compartir la pantalla exacta?")));

        String payload = new ObjectMapper().writeValueAsString(
                new TestPayload(
                        "No carga el boton guardar",
                        "/dashboard/dev",
                        "dev",
                        "dev",
                        "DEV",
                        "task-123",
                        "BUG",
                        "HIGH",
                        "Estor",
                        false,
                        "default"));

        mockMvc.perform(post("/api/feedback-chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
            .requestAttr(RequestContext.REQUEST_ID, "req-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Accion aplicada"))
            .andExpect(jsonPath("$.provider").value("openai"))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.requestId").value("req-123"))
            .andExpect(jsonPath("$.usage.promptTokens").value(120))
            .andExpect(jsonPath("$.usage.completionTokens").value(45))
            .andExpect(jsonPath("$.usage.cachedPromptTokens").value(10))
            .andExpect(jsonPath("$.usage.totalTokens").value(165))
            .andExpect(jsonPath("$.usage.estimatedCostUsd").value(0.00012345))
            .andExpect(jsonPath("$.questions[0]").value("Puedes compartir la pantalla exacta?"));
    }

    private record TestPayload(
            String message,
            String route,
            String sectionTag,
            String role,
            String roleTag,
            String taskId,
            String taskType,
            String priority,
            String userName,
            boolean isGeneralMode,
            String modelPreference) {
    }
}
