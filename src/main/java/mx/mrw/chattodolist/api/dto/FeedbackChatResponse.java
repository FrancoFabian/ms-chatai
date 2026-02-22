package mx.mrw.chattodolist.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackChatResponse(
        String reply,
        String provider,
        String model,
        String requestId,
        UsageMetricsResponse usage,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> questions) {
}
