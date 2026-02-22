package mx.mrw.chattodolist.service;

import org.springframework.ai.chat.metadata.Usage;

public record AiUsageTrackInput(
        String requestId,
        String subject,
        String flow,
        String endpoint,
        AiUsageEventType eventType,
        String taskId,
        String provider,
        String model,
        String route,
        String promptVersion,
        String normalizationMode,
        int maxTokensApplied,
        int inputChars,
        int outputChars,
        boolean truncated,
        String fallbackMode,
        boolean cacheHit,
        boolean cacheBypassBudgetMismatch,
        double temperatureApplied,
        Usage usage) {
}
