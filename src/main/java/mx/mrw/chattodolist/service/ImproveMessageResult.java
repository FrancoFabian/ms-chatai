package mx.mrw.chattodolist.service;

public record ImproveMessageResult(
        String improvedMessage,
        String provider,
        String model,
        UsageMetrics usage) {
}
