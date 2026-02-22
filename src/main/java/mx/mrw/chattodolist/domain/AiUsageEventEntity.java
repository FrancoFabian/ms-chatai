package mx.mrw.chattodolist.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_usage_events")
public class AiUsageEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(nullable = false, length = 128)
    private String subject;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "flow", nullable = false, length = 64)
    private String flow;

    @Column(name = "endpoint", nullable = false, length = 128)
    private String endpoint;

    @Column(name = "task_id", length = 128)
    private String taskId;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(name = "route", length = 1000)
    private String route;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "normalization_mode", nullable = false, length = 32)
    private String normalizationMode;

    @Column(name = "max_tokens_applied", nullable = false)
    private int maxTokensApplied;

    @Column(name = "input_chars", nullable = false)
    private int inputChars;

    @Column(name = "output_chars", nullable = false)
    private int outputChars;

    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    @Column(name = "fallback_mode", length = 64)
    private String fallbackMode;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "cache_bypass_budget_mismatch", nullable = false)
    private boolean cacheBypassBudgetMismatch;

    @Column(name = "temperature_applied", nullable = false, precision = 6, scale = 3)
    private java.math.BigDecimal temperatureApplied;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "cached_prompt_tokens", nullable = false)
    private int cachedPromptTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 18, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getFlow() {
        return flow;
    }

    public void setFlow(String flow) {
        this.flow = flow;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getNormalizationMode() {
        return normalizationMode;
    }

    public void setNormalizationMode(String normalizationMode) {
        this.normalizationMode = normalizationMode;
    }

    public int getMaxTokensApplied() {
        return maxTokensApplied;
    }

    public void setMaxTokensApplied(int maxTokensApplied) {
        this.maxTokensApplied = maxTokensApplied;
    }

    public int getInputChars() {
        return inputChars;
    }

    public void setInputChars(int inputChars) {
        this.inputChars = inputChars;
    }

    public int getOutputChars() {
        return outputChars;
    }

    public void setOutputChars(int outputChars) {
        this.outputChars = outputChars;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getFallbackMode() {
        return fallbackMode;
    }

    public void setFallbackMode(String fallbackMode) {
        this.fallbackMode = fallbackMode;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public boolean isCacheBypassBudgetMismatch() {
        return cacheBypassBudgetMismatch;
    }

    public void setCacheBypassBudgetMismatch(boolean cacheBypassBudgetMismatch) {
        this.cacheBypassBudgetMismatch = cacheBypassBudgetMismatch;
    }

    public java.math.BigDecimal getTemperatureApplied() {
        return temperatureApplied;
    }

    public void setTemperatureApplied(java.math.BigDecimal temperatureApplied) {
        this.temperatureApplied = temperatureApplied;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getCachedPromptTokens() {
        return cachedPromptTokens;
    }

    public void setCachedPromptTokens(int cachedPromptTokens) {
        this.cachedPromptTokens = cachedPromptTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
