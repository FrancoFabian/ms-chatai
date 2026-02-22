package mx.mrw.chattodolist.service;

import java.util.List;

public record UsageSummaryData(
        UsageTotalsData totals,
        List<UsageByModelData> byModel) {
}
