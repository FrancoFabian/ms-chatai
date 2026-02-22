package mx.mrw.chattodolist.api.dto;

import java.math.BigDecimal;

public record UsageByModelResponse(
        String model,
        long eventCount,
        int promptTokens,
        int completionTokens,
        int cachedPromptTokens,
        int totalTokens,
        BigDecimal estimatedCostUsd) {
}
