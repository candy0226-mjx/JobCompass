-- Historical runs intentionally remain NULL; scores are bound to their original model report.
ALTER TABLE analysis_run ADD COLUMN score_json jsonb;
