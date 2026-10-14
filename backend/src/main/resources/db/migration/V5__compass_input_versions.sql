-- Additive only: legacy tables, routes and historical analyses remain intact.
ALTER TABLE job_descriptions ADD COLUMN compass_hash VARCHAR(64);
ALTER TABLE job_descriptions ADD COLUMN compass_location VARCHAR(200);
ALTER TABLE job_descriptions ADD COLUMN compass_source_url VARCHAR(2000);
ALTER TABLE job_descriptions ADD COLUMN compass_deadline DATE;
CREATE INDEX ix_compass_job_content ON job_descriptions(company,title,compass_hash);
CREATE TABLE resume_version (
    id UUID PRIMARY KEY,
    resume_id UUID NOT NULL REFERENCES resumes(id) ON DELETE RESTRICT,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    raw_text TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    segments_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(resume_id,version_no)
);
