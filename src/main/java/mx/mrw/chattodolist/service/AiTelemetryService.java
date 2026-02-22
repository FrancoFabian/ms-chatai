package mx.mrw.chattodolist.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import mx.mrw.chattodolist.domain.AiUsageEventEntity;
import mx.mrw.chattodolist.domain.AiUsageEventRepository;

@Service
public class AiTelemetryService {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final AiUsageEventRepository usageEventRepository;

    private final BigDecimal defaultInputPer1MUsd;
    private final BigDecimal defaultCachedInputPer1MUsd;
    private final BigDecimal defaultOutputPer1MUsd;

    private final BigDecimal gpt5MiniInputPer1MUsd;
    private final BigDecimal gpt5MiniCachedInputPer1MUsd;
    private final BigDecimal gpt5MiniOutputPer1MUsd;

    public AiTelemetryService(
            AiUsageEventRepository usageEventRepository,
            @Value("${app.ai.pricing.default.input-per-1m-usd:0.150}") BigDecimal defaultInputPer1MUsd,
            @Value("${app.ai.pricing.default.cached-input-per-1m-usd:0.000}") BigDecimal defaultCachedInputPer1MUsd,
            @Value("${app.ai.pricing.default.output-per-1m-usd:0.600}") BigDecimal defaultOutputPer1MUsd,
            @Value("${app.ai.pricing.gpt-5-mini.input-per-1m-usd:0.250}") BigDecimal gpt5MiniInputPer1MUsd,
            @Value("${app.ai.pricing.gpt-5-mini.cached-input-per-1m-usd:0.025}") BigDecimal gpt5MiniCachedInputPer1MUsd,
            @Value("${app.ai.pricing.gpt-5-mini.output-per-1m-usd:2.000}") BigDecimal gpt5MiniOutputPer1MUsd) {
        this.usageEventRepository = usageEventRepository;
        this.defaultInputPer1MUsd = defaultInputPer1MUsd;
        this.defaultCachedInputPer1MUsd = defaultCachedInputPer1MUsd;
        this.defaultOutputPer1MUsd = defaultOutputPer1MUsd;
        this.gpt5MiniInputPer1MUsd = gpt5MiniInputPer1MUsd;
        this.gpt5MiniCachedInputPer1MUsd = gpt5MiniCachedInputPer1MUsd;
        this.gpt5MiniOutputPer1MUsd = gpt5MiniOutputPer1MUsd;
    }

    public UsageMetrics track(AiUsageTrackInput input) {
        Usage usage = input.usage();
        int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int cachedPromptTokens = extractCachedPromptTokens(usage);
        if (cachedPromptTokens > promptTokens) {
            cachedPromptTokens = promptTokens;
        }
        int totalTokens = promptTokens + completionTokens;

        ModelPricing pricing = pricingForModel(input.model());
        BigDecimal nonCachedPromptCost = priceForTokens(promptTokens - cachedPromptTokens, pricing.inputPer1MUsd());
        BigDecimal cachedPromptCost = priceForTokens(cachedPromptTokens, pricing.cachedInputPer1MUsd());
        BigDecimal completionCost = priceForTokens(completionTokens, pricing.outputPer1MUsd());
        BigDecimal estimatedCostUsd = nonCachedPromptCost
            .add(cachedPromptCost)
            .add(completionCost)
            .setScale(8, RoundingMode.HALF_UP);

        AiUsageEventEntity eventEntity = new AiUsageEventEntity();
        eventEntity.setRequestId(input.requestId());
        eventEntity.setSubject(StringUtils.hasText(input.subject()) ? input.subject() : "unknown");
        eventEntity.setEventType(input.eventType().name());
        eventEntity.setFlow(StringUtils.hasText(input.flow()) ? input.flow() : "unknown");
        eventEntity.setEndpoint(StringUtils.hasText(input.endpoint()) ? input.endpoint() : "unknown");
        eventEntity.setTaskId(input.taskId());
        eventEntity.setProvider(input.provider());
        eventEntity.setModel(input.model());
        eventEntity.setRoute(input.route());
        eventEntity.setPromptVersion(StringUtils.hasText(input.promptVersion()) ? input.promptVersion() : "v1");
        eventEntity.setNormalizationMode(StringUtils.hasText(input.normalizationMode()) ? input.normalizationMode() : "none");
        eventEntity.setMaxTokensApplied(Math.max(0, input.maxTokensApplied()));
        eventEntity.setInputChars(Math.max(0, input.inputChars()));
        eventEntity.setOutputChars(Math.max(0, input.outputChars()));
        eventEntity.setTruncated(input.truncated());
        eventEntity.setFallbackMode(input.fallbackMode());
        eventEntity.setCacheHit(input.cacheHit());
        eventEntity.setCacheBypassBudgetMismatch(input.cacheBypassBudgetMismatch());
        eventEntity.setTemperatureApplied(BigDecimal.valueOf(input.temperatureApplied()).setScale(3, RoundingMode.HALF_UP));
        eventEntity.setPromptTokens(promptTokens);
        eventEntity.setCompletionTokens(completionTokens);
        eventEntity.setCachedPromptTokens(cachedPromptTokens);
        eventEntity.setTotalTokens(totalTokens);
        eventEntity.setEstimatedCostUsd(estimatedCostUsd);
        usageEventRepository.save(eventEntity);

        return new UsageMetrics(promptTokens, completionTokens, cachedPromptTokens, totalTokens, estimatedCostUsd);
    }

    public UsageSummaryData summaryForSubject(String subject) {
        String subjectKey = StringUtils.hasText(subject) ? subject : "unknown";
        List<AiUsageEventEntity> events = usageEventRepository.findBySubjectOrderByCreatedAtDesc(subjectKey);

        int totalPrompt = 0;
        int totalCompletion = 0;
        int totalCachedPrompt = 0;
        int totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<String, MutableAggregate> byModel = new HashMap<>();

        for (AiUsageEventEntity event : events) {
            totalPrompt += event.getPromptTokens();
            totalCompletion += event.getCompletionTokens();
            totalCachedPrompt += event.getCachedPromptTokens();
            totalTokens += event.getTotalTokens();
            totalCost = totalCost.add(zeroIfNull(event.getEstimatedCostUsd()));

            MutableAggregate aggregate = byModel.computeIfAbsent(event.getModel(), ignored -> new MutableAggregate());
            aggregate.eventCount++;
            aggregate.promptTokens += event.getPromptTokens();
            aggregate.completionTokens += event.getCompletionTokens();
            aggregate.cachedPromptTokens += event.getCachedPromptTokens();
            aggregate.totalTokens += event.getTotalTokens();
            aggregate.estimatedCostUsd = aggregate.estimatedCostUsd.add(zeroIfNull(event.getEstimatedCostUsd()));
        }

        List<UsageByModelData> byModelList = new ArrayList<>();
        for (Map.Entry<String, MutableAggregate> entry : byModel.entrySet()) {
            MutableAggregate value = entry.getValue();
            byModelList.add(new UsageByModelData(
                    entry.getKey(),
                    value.eventCount,
                    value.promptTokens,
                    value.completionTokens,
                    value.cachedPromptTokens,
                    value.totalTokens,
                    value.estimatedCostUsd.setScale(8, RoundingMode.HALF_UP)));
        }
        byModelList.sort(Comparator.comparing(UsageByModelData::estimatedCostUsd).reversed());

        UsageTotalsData totals = new UsageTotalsData(
                events.size(),
                totalPrompt,
                totalCompletion,
                totalCachedPrompt,
                totalTokens,
                totalCost.setScale(8, RoundingMode.HALF_UP));
        return new UsageSummaryData(totals, byModelList);
    }

    private int extractCachedPromptTokens(Usage usage) {
        if (usage == null || usage.getNativeUsage() == null) {
            return 0;
        }
        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage instanceof Map<?, ?> map) {
            Object details = map.get("input_tokens_details");
            if (details instanceof Map<?, ?> detailsMap) {
                Object cachedTokens = detailsMap.get("cached_tokens");
                if (cachedTokens instanceof Number number) {
                    return Math.max(0, number.intValue());
                }
            }
        }
        return 0;
    }

    private BigDecimal priceForTokens(int tokenCount, BigDecimal pricePer1M) {
        if (tokenCount <= 0) {
            return BigDecimal.ZERO;
        }
        return pricePer1M
            .multiply(BigDecimal.valueOf(tokenCount))
            .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
    }

    private ModelPricing pricingForModel(String model) {
        if ("gpt-5-mini".equalsIgnoreCase(model)) {
            return new ModelPricing(gpt5MiniInputPer1MUsd, gpt5MiniCachedInputPer1MUsd, gpt5MiniOutputPer1MUsd);
        }
        return new ModelPricing(defaultInputPer1MUsd, defaultCachedInputPer1MUsd, defaultOutputPer1MUsd);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ModelPricing(
            BigDecimal inputPer1MUsd,
            BigDecimal cachedInputPer1MUsd,
            BigDecimal outputPer1MUsd) {
    }

    private static final class MutableAggregate {
        private long eventCount;
        private int promptTokens;
        private int completionTokens;
        private int cachedPromptTokens;
        private int totalTokens;
        private BigDecimal estimatedCostUsd = BigDecimal.ZERO;
    }
}
