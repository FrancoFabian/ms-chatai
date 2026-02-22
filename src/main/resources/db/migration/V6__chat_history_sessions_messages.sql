CREATE TABLE IF NOT EXISTS chat_sessions (
    id VARCHAR(128) PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    title VARCHAR(180) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_subject_updated_at
    ON chat_sessions (subject, updated_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
    id VARCHAR(128) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    sender VARCHAR(8) NOT NULL,
    text TEXT NOT NULL,
    task_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created_at
    ON chat_messages (session_id, created_at ASC);

CREATE TABLE IF NOT EXISTS chat_message_attachments (
    id UUID PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL,
    media_path VARCHAR(512) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_message_attachments_message
        FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_message_attachments_message_created_at
    ON chat_message_attachments (message_id, created_at ASC);
