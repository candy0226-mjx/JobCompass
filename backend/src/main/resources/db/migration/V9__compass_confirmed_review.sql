ALTER TABLE requirement_review DROP CONSTRAINT requirement_review_category_check;
ALTER TABLE requirement_review ADD CONSTRAINT requirement_review_category_check CHECK(category IN ('EVIDENCE_FIX','LEARN','CLARIFY','CONFIRMED_MATCH'));
