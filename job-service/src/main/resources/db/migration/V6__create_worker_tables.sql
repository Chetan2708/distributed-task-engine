CREATE TABLE IF NOT EXISTS scheduled_retries (
    id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    execute_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scheduled_retries_execute_at ON scheduled_retries(execute_at);

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_messages_job_id ON processed_messages(job_id);
