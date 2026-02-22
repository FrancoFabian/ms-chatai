CREATE TABLE IF NOT EXISTS ai_usage_events (
    id UUID PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    task_id VARCHAR(128),
    provider VARCHAR(16) NOT NULL,
    model VARCHAR(64) NOT NULL,
    route VARCHAR(1000),
    message_chars INTEGER NOT NULL DEFAULT 0,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    cached_prompt_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(18, 8) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_subject_created_at
    ON ai_usage_events (subject, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_model
    ON ai_usage_events (model);
