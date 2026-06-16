-- Contest scoring hardening migration.
-- Adds performance indexes required by P0-1 (submission-id reverse lookup) and
-- P1-5 (rating preload HashMap). Also adds a covering index for username lookup
-- surfaced by the contest analytics review.
--
-- NOTE: this migration is additive (indexes only) and is safe to apply against
-- existing production data; it does not modify or delete any rows.

-- Optimizes P0-1: reverse lookup from submissionId -> contest submission
CREATE INDEX idx_contest_submissions_submission_id ON contest_submissions (submission_id);

-- Optimizes P1-5: rating preload HashMap (covering index for user_id + rating)
CREATE INDEX idx_global_rankings_user_id_rating ON global_rankings (user_id, rating);

-- Optimizes analytics queries by username
CREATE INDEX idx_global_rankings_username ON global_rankings (username);
