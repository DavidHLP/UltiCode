-- Add index for Notification.category filtering
CREATE INDEX `notifications_user_id_category_idx` ON `notifications`(`user_id`, `category`);

-- Add composite index for ContestRanking virtual contest support
CREATE INDEX `contest_rankings_contest_id_is_virtual_rank_idx` ON `contest_rankings`(`contest_id`, `is_virtual`, `rank`);

-- Add composite index for GlobalRanking country-specific rankings
CREATE INDEX `global_rankings_country_global_rank_idx` ON `global_rankings`(`country`, `global_rank`);

-- Add composite index for Contest visible contest listings
CREATE INDEX `contests_status_is_visible_start_time_idx` ON `contests`(`status`, `is_visible`, `start_time`);

-- Add composite index for ContestSubmission time-ordered queries
CREATE INDEX `contest_submissions_contest_id_participant_id_submitted_at_idx` ON `contest_submissions`(`contest_id`, `participant_id`, `submitted_at`);

-- Add composite index for Submission best submission lookup
CREATE INDEX `submissions_problem_id_user_id_status_runtime_memory_idx` ON `submissions`(`problem_id`, `user_id`, `status`, `runtime`, `memory`);
