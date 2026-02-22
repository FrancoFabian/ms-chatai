CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL UNIQUE,
    route VARCHAR(1000) NOT NULL,
    section_tag VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    role_tag VARCHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    user_name VARCHAR(120),
    is_general_mode BOOLEAN NOT NULL,
    user_message TEXT NOT NULL,
    assistant_reply TEXT NOT NULL,
    ai_provider VARCHAR(16) NOT NULL,
    ai_model VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_usage_events (
    id UUID PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    flow VARCHAR(64) NOT NULL DEFAULT 'unknown',
    endpoint VARCHAR(128) NOT NULL DEFAULT 'unknown',
    task_id VARCHAR(128),
    provider VARCHAR(16) NOT NULL,
    model VARCHAR(64) NOT NULL,
    route VARCHAR(1000),
    prompt_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    normalization_mode VARCHAR(32) NOT NULL DEFAULT 'none',
    max_tokens_applied INTEGER NOT NULL DEFAULT 0,
    input_chars INTEGER NOT NULL DEFAULT 0,
    output_chars INTEGER NOT NULL DEFAULT 0,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_mode VARCHAR(64),
    cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    cache_bypass_budget_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
    temperature_applied NUMERIC(6, 3) NOT NULL DEFAULT 0,
    message_chars INTEGER NOT NULL DEFAULT 0,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    cached_prompt_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(18, 8) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE IF EXISTS ai_usage_events
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS subject VARCHAR(128) NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS flow VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS endpoint VARCHAR(128) NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS task_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider VARCHAR(16) NOT NULL DEFAULT 'openai',
    ADD COLUMN IF NOT EXISTS model VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS route VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    ADD COLUMN IF NOT EXISTS normalization_mode VARCHAR(32) NOT NULL DEFAULT 'none',
    ADD COLUMN IF NOT EXISTS max_tokens_applied INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS input_chars INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS output_chars INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS truncated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fallback_mode VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cache_bypass_budget_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS temperature_applied NUMERIC(6, 3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS message_chars INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS completion_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cached_prompt_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(18, 8) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_subject_created_at
    ON ai_usage_events (subject, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_model
    ON ai_usage_events (model);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_flow_created_at
    ON ai_usage_events (flow, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_cache_hit
    ON ai_usage_events (cache_hit);
