CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_created_at ON jobs(created_at);
CREATE INDEX idx_jobs_type ON jobs(type);
CREATE INDEX idx_jobs_status_created_at ON jobs(status, created_at);
