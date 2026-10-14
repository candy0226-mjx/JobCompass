CREATE TABLE preparation_plan (
 id UUID PRIMARY KEY, status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED','ARCHIVED')),
 budget_minutes INTEGER NOT NULL CHECK(budget_minutes BETWEEN 30 AND 2400 AND budget_minutes%30=0),
 allocated_minutes INTEGER NOT NULL DEFAULT 0 CHECK(allocated_minutes >=0 AND allocated_minutes<=budget_minutes),
 version BIGINT NOT NULL DEFAULT 0, rule_version VARCHAR(40) NOT NULL DEFAULT 'budget-v1',
 confirmation_key VARCHAR(120) UNIQUE, confirmation_hash VARCHAR(64),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), confirmed_at TIMESTAMPTZ
);
CREATE TABLE plan_analysis (
 plan_id UUID NOT NULL REFERENCES preparation_plan(id) ON DELETE RESTRICT,
 analysis_id UUID NOT NULL REFERENCES analysis_run(id) ON DELETE RESTRICT,
 priority INTEGER NOT NULL CHECK(priority BETWEEN 1 AND 3),
 review_snapshot_json JSONB NOT NULL, PRIMARY KEY(plan_id,analysis_id)
);
CREATE TABLE plan_task (
 id UUID PRIMARY KEY, plan_id UUID NOT NULL REFERENCES preparation_plan(id) ON DELETE RESTRICT,
 task_json JSONB NOT NULL, selected BOOLEAN NOT NULL, effort_minutes INTEGER NOT NULL CHECK(effort_minutes BETWEEN 30 AND 960 AND effort_minutes%30=0),
 status VARCHAR(20) NOT NULL DEFAULT 'TODO' CHECK(status IN ('TODO','DOING','DONE','SKIPPED')), version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_compass_plan_tasks ON plan_task(plan_id);
CREATE TABLE task_requirement (
 task_id UUID NOT NULL REFERENCES plan_task(id) ON DELETE RESTRICT,
 requirement_id UUID NOT NULL REFERENCES analysis_requirement(id) ON DELETE RESTRICT,
 PRIMARY KEY(task_id,requirement_id)
);
CREATE TABLE job_application (
 id UUID PRIMARY KEY, job_id UUID NOT NULL UNIQUE REFERENCES job_descriptions(id) ON DELETE RESTRICT,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PLANNED','APPLIED','INTERVIEW','OFFER','REJECTED','WITHDRAWN')),
 note VARCHAR(1000) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
