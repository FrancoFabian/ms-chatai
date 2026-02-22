package mx.mrw.chattodolist.api.dto;

import java.util.List;

public record UsageSummaryResponse(
        String requestId,
        String currency,
        UsageTotalsResponse totals,
        List<UsageByModelResponse> byModel) {
}
