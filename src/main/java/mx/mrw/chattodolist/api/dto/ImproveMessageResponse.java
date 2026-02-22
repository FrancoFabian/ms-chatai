package mx.mrw.chattodolist.api.dto;

public record ImproveMessageResponse(
        String improvedMessage,
        String provider,
        String model,
        String requestId,
        UsageMetricsResponse usage) {
}
