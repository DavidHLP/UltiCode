-- Manual rollback for V20260811180000__Create_App_Contest_Schema.sql.
-- Flyway does not execute rollback files automatically.
-- Run only after stopping App writers and taking a verified backup.

-- Restore values that cannot be represented by the original baseline schema.
UPDATE contests
SET status = 'RUNNING'
WHERE status = 'FINISHING';

UPDATE contests
SET end_time = COALESCE(end_time, start_time)
WHERE end_time IS NULL;

ALTER TABLE contest_adjudication_receipts DROP FOREIGN KEY fk_app_contest_adjudication_receipts_submission;
ALTER TABLE contest_submissions DROP FOREIGN KEY fk_app_contest_submissions_participant_contest;
ALTER TABLE contest_submissions DROP FOREIGN KEY fk_app_contest_submissions_problem_contest;
ALTER TABLE contest_submissions DROP FOREIGN KEY fk_app_contest_submissions_contest;
ALTER TABLE contest_problem_results DROP FOREIGN KEY fk_app_contest_problem_results_ranking_contest;
ALTER TABLE contest_problem_results DROP FOREIGN KEY fk_app_contest_problem_results_participant_contest;
ALTER TABLE contest_problem_results DROP FOREIGN KEY fk_app_contest_problem_results_problem_contest;
ALTER TABLE contest_problem_results DROP FOREIGN KEY fk_app_contest_problem_results_contest;
ALTER TABLE contest_analytics DROP FOREIGN KEY fk_app_contest_analytics_contest;
ALTER TABLE contest_announcements DROP FOREIGN KEY fk_app_contest_announcements_contest;
ALTER TABLE contest_rating_calculations DROP FOREIGN KEY fk_app_contest_rating_calculations_contest;
ALTER TABLE first_solve_records DROP FOREIGN KEY fk_app_first_solve_records_contest;
ALTER TABLE virtual_contest_sessions DROP FOREIGN KEY fk_app_virtual_contest_sessions_contest;
ALTER TABLE contest_rankings DROP FOREIGN KEY fk_app_contest_rankings_contest;
ALTER TABLE contest_participants DROP FOREIGN KEY fk_app_contest_participants_contest;
ALTER TABLE contest_problems DROP FOREIGN KEY fk_app_contest_problems_contest;
ALTER TABLE contests DROP FOREIGN KEY fk_app_contests_scoring_rule;

DROP TABLE contest_adjudication_receipts;
DROP TABLE contest_rating_calculations;
DROP TABLE contest_announcements;
DROP TABLE contest_analytics;
DROP TABLE first_solve_records;
DROP TABLE contest_problem_results;
DROP TABLE contest_submissions;
DROP TABLE virtual_contest_sessions;
DROP TABLE contest_rankings;
DROP TABLE contest_participants;
DROP TABLE contest_problems;
DROP TABLE contest_scoring_rules;
DROP TABLE global_rankings;

ALTER TABLE submissions
    DROP KEY idx_lease_expiry,
    DROP COLUMN judging_lease_expires_at,
    DROP COLUMN current_attempt_id,
    DROP COLUMN generation;

ALTER TABLE contests
    DROP KEY contests_status_start_time_idx,
    DROP KEY contests_contest_type_idx,
    DROP KEY contests_status_is_visible_start_time_idx,
    DROP KEY contests_scoring_rule_id_fkey,
    DROP KEY idx_contests_created_by,
    DROP KEY idx_contests_is_virtual,
    DROP KEY idx_contests_status_type,
    DROP COLUMN contest_type,
    DROP COLUMN duration_minutes,
    DROP COLUMN actual_start_time,
    DROP COLUMN actual_end_time,
    DROP COLUMN registration_start,
    DROP COLUMN registration_end,
    DROP COLUMN freeze_time,
    DROP COLUMN penalty_per_wrong,
    DROP COLUMN scoring_mode,
    DROP COLUMN tie_breaker,
    DROP COLUMN registered_count,
    DROP COLUMN participant_count,
    DROP COLUMN is_rated,
    DROP COLUMN description,
    DROP COLUMN cover_image,
    DROP COLUMN created_by,
    DROP COLUMN is_visible,
    DROP COLUMN rules,
    DROP COLUMN updated_at,
    DROP COLUMN is_deleted,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by,
    DROP COLUMN is_virtual,
    DROP COLUMN max_participants,
    DROP COLUMN scoring_rule_id,
    DROP COLUMN submission_count,
    MODIFY COLUMN title VARCHAR(120) NOT NULL,
    MODIFY COLUMN end_time DATETIME(3) NOT NULL,
    MODIFY COLUMN status ENUM('DRAFT', 'UPCOMING', 'RUNNING', 'FINISHED', 'CANCELLED')
        NOT NULL DEFAULT 'DRAFT';
