CREATE TABLE analysis_run (
 id UUID PRIMARY KEY, resume_version_id UUID NOT NULL REFERENCES resume_version(id) ON DELETE RESTRICT,
 job_id UUID NOT NULL REFERENCES job_descriptions(id) ON DELETE RESTRICT,
 job_snapshot_json JSONB NOT NULL, status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
 report_json JSONB, error_code VARCHAR(80), model_name VARCHAR(200), prompt_version VARCHAR(40) NOT NULL DEFAULT 'evidence-v1',
 duration_ms BIGINT, tool_calls INTEGER DEFAULT 0, retry_of UUID REFERENCES analysis_run(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ
);
CREATE INDEX ix_compass_runs_status ON analysis_run(status);
CREATE TABLE analysis_requirement (
 id UUID PRIMARY KEY, analysis_id UUID NOT NULL REFERENCES analysis_run(id) ON DELETE RESTRICT,
 requirement_json JSONB NOT NULL, assessment_json JSONB NOT NULL
);
CREATE TABLE requirement_review (
 requirement_id UUID PRIMARY KEY REFERENCES analysis_requirement(id) ON DELETE RESTRICT,
 category VARCHAR(30) NOT NULL CHECK(category IN ('EVIDENCE_FIX','LEARN','CLARIFY')),
 note VARCHAR(1000) NOT NULL, version BIGINT NOT NULL DEFAULT 1, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE activity_event (
 id UUID PRIMARY KEY, resource_type VARCHAR(40) NOT NULL, resource_id UUID NOT NULL,
 old_value VARCHAR(1000), new_value VARCHAR(1000), version BIGINT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_compass_events_resource ON activity_event(resource_id,occurred_at);
