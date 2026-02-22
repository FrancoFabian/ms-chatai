package mx.mrw.chattodolist.service;

import java.math.BigDecimal;

public record UsageTotalsData(
        long eventCount,
        int promptTokens,
        int completionTokens,
        int cachedPromptTokens,
        int totalTokens,
        BigDecimal estimatedCostUsd) {
}
