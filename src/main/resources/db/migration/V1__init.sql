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
