ALTER TABLE IF EXISTS tasks
    ADD COLUMN IF NOT EXISTS subject VARCHAR(128) NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS title VARCHAR(240) NOT NULL DEFAULT 'Untitled task',
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE tasks
SET updated_at = created_at
WHERE updated_at IS NULL;

UPDATE tasks
SET title = LEFT(COALESCE(NULLIF(TRIM(user_message), ''), 'Untitled task'), 240)
WHERE title IS NULL OR TRIM(title) = '';

CREATE INDEX IF NOT EXISTS idx_tasks_subject_updated_at
    ON tasks (subject, updated_at DESC);

CREATE TABLE IF NOT EXISTS task_dev_notes (
    id UUID PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    author_name VARCHAR(120),
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_dev_notes_task_id
        FOREIGN KEY (task_id) REFERENCES tasks(task_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_dev_notes_task_id_created_at
    ON task_dev_notes (task_id, created_at ASC);
