CREATE TABLE experience_inquiry (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis_run(id) ON DELETE RESTRICT,
    requirement_id UUID NOT NULL UNIQUE REFERENCES analysis_requirement(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('QUESTIONS_PENDING','ANSWERING','CARD_DRAFT','CONFIRMED','FAILED')),
    questions_json JSONB NOT NULL DEFAULT '[]',
    answers_json JSONB NOT NULL DEFAULT '[]',
    draft_json JSONB,
    version BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    duration_ms BIGINT,
    model_name VARCHAR(200),
    prompt_version VARCHAR(40) NOT NULL DEFAULT 'experience-v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_experience_inquiry_analysis ON experience_inquiry(analysis_id);

-- Confirmed cards are immutable snapshots; editing answers creates a new draft.
CREATE TABLE experience_evidence_card (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL REFERENCES experience_inquiry(id) ON DELETE RESTRICT,
    answer_version BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL CHECK (source_type = 'USER_CONFIRMED'),
    questions_json JSONB NOT NULL,
    answers_json JSONB NOT NULL,
    card_json JSONB NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (inquiry_id, answer_version)
);
