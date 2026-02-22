package mx.mrw.chattodolist.api.dto;

import java.math.BigDecimal;

public record UsageMetricsResponse(
        int promptTokens,
        int completionTokens,
        int cachedPromptTokens,
        int totalTokens,
        BigDecimal estimatedCostUsd) {
}
