package mx.mrw.chattodolist.api.dto;

import java.math.BigDecimal;

public record UsageTotalsResponse(
        long eventCount,
        int promptTokens,
        int completionTokens,
        int cachedPromptTokens,
        int totalTokens,
        BigDecimal estimatedCostUsd) {
}
