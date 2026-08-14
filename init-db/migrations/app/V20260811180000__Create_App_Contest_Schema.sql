-- CONTEST-010: close the App owner's independent contest schema.
--
-- This migration is intentionally self-contained: flyway-app.conf scans only
-- migrations/app, so the App service must not depend on the shared migration
-- chain for contest tables or later contest hardening.
--
-- user_id and problem_id remain contract references. No Auth-owned users table
-- or Problem cross-owner foreign key is created here. Ranking identity is read
-- through the App owner port and Auth identity RPC, not a cross-schema SQL join.
SET NAMES utf8mb4;

-- The first App schema migration created a deliberately small contests table.
-- The shared baseline can already contain the expanded shape, so every ADD is
-- guarded instead of relying on MySQL's unsupported ADD COLUMN IF NOT EXISTS.
SET @ddl := 'ALTER TABLE `contests`
    MODIFY COLUMN `title` VARCHAR(200) NOT NULL,
    MODIFY COLUMN `end_time` DATETIME(3) DEFAULT NULL,
    MODIFY COLUMN `status` ENUM(''DRAFT'', ''UPCOMING'', ''RUNNING'', ''FINISHING'', ''FINISHED'', ''CANCELLED'')
        NOT NULL DEFAULT ''DRAFT''';

SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'contest_type') = 0,
    ', ADD COLUMN `contest_type` ENUM(''ICPC'', ''IOI'', ''CUSTOM'') NOT NULL DEFAULT ''CUSTOM''',
    ', MODIFY COLUMN `contest_type` ENUM(''ICPC'', ''IOI'', ''CUSTOM'') NOT NULL DEFAULT ''CUSTOM'''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'duration_minutes') = 0,
    ', ADD COLUMN `duration_minutes` INT NOT NULL DEFAULT 0',
    ', MODIFY COLUMN `duration_minutes` INT NOT NULL DEFAULT 0'));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'actual_start_time') = 0,
    ', ADD COLUMN `actual_start_time` DATETIME(3) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'actual_end_time') = 0,
    ', ADD COLUMN `actual_end_time` DATETIME(3) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'registration_start') = 0,
    ', ADD COLUMN `registration_start` DATETIME(3) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'registration_end') = 0,
    ', ADD COLUMN `registration_end` DATETIME(3) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'freeze_time') = 0,
    ', ADD COLUMN `freeze_time` DATETIME(3) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'penalty_per_wrong') = 0,
    ', ADD COLUMN `penalty_per_wrong` INT NOT NULL DEFAULT 300', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'scoring_mode') = 0,
    ', ADD COLUMN `scoring_mode` ENUM(''SCORE'', ''ICPC'', ''IOI'') NOT NULL DEFAULT ''SCORE''', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'tie_breaker') = 0,
    ', ADD COLUMN `tie_breaker` ENUM(''LAST_SOLVE_TIME'', ''TOTAL_TIME'', ''TOTAL_ATTEMPTS'', ''NONE'') NOT NULL DEFAULT ''LAST_SOLVE_TIME''',
    ', MODIFY COLUMN `tie_breaker` ENUM(''LAST_SOLVE_TIME'', ''TOTAL_TIME'', ''TOTAL_ATTEMPTS'', ''NONE'') NOT NULL DEFAULT ''LAST_SOLVE_TIME'''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'registered_count') = 0,
    ', ADD COLUMN `registered_count` INT NOT NULL DEFAULT 0', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'participant_count') = 0,
    ', ADD COLUMN `participant_count` INT NOT NULL DEFAULT 0', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'is_rated') = 0,
    ', ADD COLUMN `is_rated` TINYINT(1) NOT NULL DEFAULT 1', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'description') = 0,
    ', ADD COLUMN `description` TEXT DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'cover_image') = 0,
    ', ADD COLUMN `cover_image` VARCHAR(255) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'created_by') = 0,
    ', ADD COLUMN `created_by` VARCHAR(40) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'is_visible') = 0,
    ', ADD COLUMN `is_visible` TINYINT(1) NOT NULL DEFAULT 1', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'rules') = 0,
    ', ADD COLUMN `rules` TEXT DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'updated_at') = 0,
    ', ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)',
    ', MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)'));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'is_deleted') = 0,
    ', ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'deleted_at') = 0,
    ', ADD COLUMN `deleted_at` DATETIME(3) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'deleted_by') = 0,
    ', ADD COLUMN `deleted_by` VARCHAR(40) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'is_virtual') = 0,
    ', ADD COLUMN `is_virtual` TINYINT(1) NOT NULL DEFAULT 0', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'max_participants') = 0,
    ', ADD COLUMN `max_participants` INT DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'scoring_rule_id') = 0,
    ', ADD COLUMN `scoring_rule_id` VARCHAR(40) DEFAULT NULL', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND COLUMN_NAME = 'submission_count') = 0,
    ', ADD COLUMN `submission_count` INT NOT NULL DEFAULT 0', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'contests_status_start_time_idx') = 0,
    ', ADD KEY `contests_status_start_time_idx` (`status`, `start_time`)', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'contests_contest_type_idx') = 0,
    ', ADD KEY `contests_contest_type_idx` (`contest_type`)', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'contests_status_is_visible_start_time_idx') = 0,
    ', ADD KEY `contests_status_is_visible_start_time_idx` (`status`, `is_visible`, `start_time`)', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'contests_scoring_rule_id_fkey') = 0,
    ', ADD KEY `contests_scoring_rule_id_fkey` (`scoring_rule_id`)', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'idx_contests_created_by') = 0,
    ', ADD KEY `idx_contests_created_by` (`created_by`)', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'idx_contests_is_virtual') = 0,
    ', ADD KEY `idx_contests_is_virtual` (`is_virtual`)', ''));
SET @ddl := CONCAT(@ddl, IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contests' AND INDEX_NAME = 'idx_contests_status_type') = 0,
    ', ADD KEY `idx_contests_status_type` (`status`, `contest_type`)', ''));

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Contest adjudication reads the submission generation fence. Keep the App
-- schema aligned with the contest receipt query without granting Auth access.
SET @generation_missing := (
    SELECT COUNT(*) = 0 FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'generation'
);
SET @attempt_missing := (
    SELECT COUNT(*) = 0 FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'current_attempt_id'
);
SET @lease_missing := (
    SELECT COUNT(*) = 0 FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'judging_lease_expires_at'
);
SET @lease_index_missing := (
    SELECT COUNT(*) = 0 FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND INDEX_NAME = 'idx_lease_expiry'
);
SET @submission_clauses := @generation_missing + @attempt_missing + @lease_missing + @lease_index_missing;
SET @ddl := CONCAT(
    'ALTER TABLE `submissions`',
    IF(@generation_missing, ' ADD COLUMN `generation` BIGINT NOT NULL DEFAULT 1', ''),
    IF(@attempt_missing, CONCAT(IF(@generation_missing, ', ADD COLUMN', ' ADD COLUMN'), ' `current_attempt_id` VARCHAR(40) DEFAULT NULL'), ''),
    IF(@lease_missing, CONCAT(IF(@generation_missing + @attempt_missing, ', ADD COLUMN', ' ADD COLUMN'), ' `judging_lease_expires_at` DATETIME(3) DEFAULT NULL'), ''),
    IF(@lease_index_missing, CONCAT(IF(@generation_missing + @attempt_missing + @lease_missing, ', ADD KEY', ' ADD KEY'), ' `idx_lease_expiry` (`status`, `judging_lease_expires_at`)'), '')
);
SET @ddl := IF(@submission_clauses = 0, 'SELECT 1', @ddl);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS contest_scoring_rules (
    id VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    base_score_per_problem INT NOT NULL DEFAULT 100,
    time_bonus_per_minute INT NOT NULL DEFAULT 1,
    wrong_answer_penalty INT NOT NULL DEFAULT 5,
    time_limit_penalty INT NOT NULL DEFAULT 0,
    first_solve_bonus INT NOT NULL DEFAULT 10,
    full_score_bonus INT NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @fk_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'contests'
       AND CONSTRAINT_NAME = 'fk_app_contests_scoring_rule'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @ddl := IF(@fk_exists = 0,
    'ALTER TABLE `contests`
        ADD CONSTRAINT `fk_app_contests_scoring_rule`
            FOREIGN KEY (`scoring_rule_id`) REFERENCES `contest_scoring_rules` (`id`)
            ON DELETE SET NULL ON UPDATE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS contest_problems (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    problem_id BIGINT NOT NULL,
    problem_index VARCHAR(10) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    penalty_per_wrong INT DEFAULT NULL,
    solved_count INT NOT NULL DEFAULT 0,
    submission_count INT NOT NULL DEFAULT 0,
    label VARCHAR(10) DEFAULT NULL,
    base_score INT DEFAULT NULL,
    time_bonus INT DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY contest_problems_contest_id_idx (contest_id),
    KEY contest_problems_problem_id_fkey (problem_id),
    UNIQUE KEY uk_contest_problem_id (contest_id, problem_id),
    UNIQUE KEY uk_app_contest_problems_id_contest (id, contest_id),
    CONSTRAINT fk_app_contest_problems_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_participants (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    status ENUM('REGISTERED', 'STARTED', 'FINISHED', 'DISQUALIFIED') NOT NULL DEFAULT 'REGISTERED',
    registered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at DATETIME(3) DEFAULT NULL,
    finished_at DATETIME(3) DEFAULT NULL,
    is_virtual TINYINT(1) NOT NULL DEFAULT 0,
    final_rank INT DEFAULT NULL,
    total_penalty INT NOT NULL DEFAULT 0,
    total_score INT NOT NULL DEFAULT 0,
    total_attempts INT NOT NULL DEFAULT 0,
    last_solve_time INT DEFAULT NULL,
    virtual_session_id VARCHAR(64) DEFAULT NULL,
    checked_in_at DATETIME(3) DEFAULT NULL,
    total_time INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_real_active TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_virtual = 0 AND status = 'STARTED' THEN 1 ELSE NULL END
    ) VIRTUAL,
    active_virtual_key VARCHAR(128) GENERATED ALWAYS AS (
        CASE WHEN is_virtual = 1 AND status = 'STARTED'
             THEN CONCAT(contest_id, '-', user_id)
             ELSE NULL
        END
    ) VIRTUAL,
    real_registration_key VARCHAR(81) GENERATED ALWAYS AS (
        CASE WHEN is_virtual = 0
             THEN CONCAT(contest_id, ':', user_id)
             ELSE NULL
        END
    ) VIRTUAL,
    virtual_active_key VARCHAR(81) GENERATED ALWAYS AS (
        CASE WHEN is_virtual = 1 AND status = 'STARTED'
             THEN CONCAT(contest_id, ':', user_id)
             ELSE NULL
        END
    ) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY contest_participants_contest_id_user_id_virtual_session_id_key
        (contest_id, user_id, virtual_session_id),
    UNIQUE KEY uk_app_contest_participants_id_contest (id, contest_id),
    UNIQUE KEY uk_real_active (contest_id, user_id, is_real_active),
    UNIQUE KEY uk_virtual_active (active_virtual_key),
    UNIQUE KEY uk_real_registration (real_registration_key),
    UNIQUE KEY uk_virtual_active_admission (virtual_active_key),
    KEY contest_participants_user_id_idx (user_id),
    KEY contest_participants_contest_id_final_rank_idx (contest_id, final_rank),
    KEY contest_participants_virtual_session_id_fkey (virtual_session_id),
    KEY contest_participants_user_id_status_is_virtual_idx (user_id, status, is_virtual),
    CONSTRAINT fk_app_contest_participants_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_rankings (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    `rank` INT NOT NULL,
    rating_before INT NOT NULL DEFAULT 1500,
    rating_after INT NOT NULL DEFAULT 1500,
    rating_change INT NOT NULL DEFAULT 0,
    is_virtual TINYINT(1) NOT NULL DEFAULT 0,
    solved_count INT NOT NULL DEFAULT 0,
    total_penalty INT NOT NULL DEFAULT 0,
    total_score INT NOT NULL DEFAULT 0,
    finish_time INT DEFAULT NULL,
    total_attempts INT NOT NULL DEFAULT 0,
    problem_stats_snapshot JSON DEFAULT NULL,
    is_frozen TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY contest_rankings_contest_id_user_id_is_virtual_key (contest_id, user_id, is_virtual),
    UNIQUE KEY uk_app_contest_rankings_id_contest (id, contest_id),
    KEY contest_rankings_contest_id_rank_idx (contest_id, `rank`),
    KEY contest_rankings_user_id_idx (user_id),
    KEY contest_rankings_contest_id_is_virtual_rank_idx (contest_id, is_virtual, `rank`),
    CONSTRAINT fk_app_contest_rankings_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS virtual_contest_sessions (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
    started_at DATETIME(3) DEFAULT NULL,
    ends_at DATETIME(3) DEFAULT NULL,
    finished_at DATETIME(3) DEFAULT NULL,
    total_score INT NOT NULL DEFAULT 0,
    total_penalty INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_virtual_contest_sessions_id_contest (id, contest_id),
    KEY virtual_contest_sessions_contest_id_user_id_idx (contest_id, user_id),
    KEY virtual_contest_sessions_user_id_status_idx (user_id, status),
    CONSTRAINT fk_app_virtual_contest_sessions_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_submissions (
    id VARCHAR(40) NOT NULL,
    submission_id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    contest_problem_id VARCHAR(40) NOT NULL,
    participant_id VARCHAR(40) NOT NULL,
    virtual_session_id VARCHAR(40) DEFAULT NULL,
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    time_from_start INT NOT NULL,
    is_accepted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_contest_submissions_submission_id (submission_id),
    KEY contest_submissions_contest_id_participant_id_idx (contest_id, participant_id),
    KEY contest_submissions_contest_problem_id_idx (contest_problem_id),
    KEY contest_submissions_participant_id_fkey (participant_id),
    KEY contest_submissions_submission_id_fkey (submission_id),
    KEY contest_submissions_contest_id_participant_id_submitted_at_idx
        (contest_id, participant_id, submitted_at),
    CONSTRAINT fk_app_contest_submissions_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_contest_submissions_problem_contest
        FOREIGN KEY (contest_problem_id, contest_id)
        REFERENCES contest_problems (id, contest_id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_contest_submissions_participant_contest
        FOREIGN KEY (participant_id, contest_id)
        REFERENCES contest_participants (id, contest_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_problem_results (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    contest_problem_id VARCHAR(40) NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    participant_id VARCHAR(40) NOT NULL,
    ranking_id VARCHAR(40) DEFAULT NULL,
    is_solved TINYINT(1) NOT NULL DEFAULT 0,
    score INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    first_solve_time INT DEFAULT NULL,
    penalty_time INT NOT NULL DEFAULT 0,
    best_submission_id VARCHAR(40) DEFAULT NULL,
    time_spent INT NOT NULL DEFAULT 0,
    time_bonus INT NOT NULL DEFAULT 0,
    is_first_solve TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY contest_problem_results_participant_id_contest_problem_id_key
        (participant_id, contest_problem_id),
    KEY contest_problem_results_contest_id_user_id_idx (contest_id, user_id),
    KEY contest_problem_results_contest_problem_id_idx (contest_problem_id),
    KEY contest_problem_results_ranking_id_fkey (ranking_id),
    KEY contest_problem_results_user_id_fkey (user_id),
    CONSTRAINT fk_app_contest_problem_results_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_contest_problem_results_problem_contest
        FOREIGN KEY (contest_problem_id, contest_id)
        REFERENCES contest_problems (id, contest_id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_contest_problem_results_participant_contest
        FOREIGN KEY (participant_id, contest_id)
        REFERENCES contest_participants (id, contest_id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_contest_problem_results_ranking_contest
        FOREIGN KEY (ranking_id, contest_id)
        REFERENCES contest_rankings (id, contest_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS first_solve_records (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    problem_id BIGINT NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    solved_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    time_spent INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY first_solve_records_contest_id_problem_id_key (contest_id, problem_id),
    KEY first_solve_records_contest_id_idx (contest_id),
    KEY first_solve_records_user_id_idx (user_id),
    KEY first_solve_records_problem_id_fkey (problem_id),
    CONSTRAINT fk_app_first_solve_records_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS global_rankings (
    id VARCHAR(40) NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    username VARCHAR(120) NOT NULL,
    global_rank INT NOT NULL,
    rating INT NOT NULL DEFAULT 1500,
    max_rating INT NOT NULL DEFAULT 1500,
    contests_attended INT NOT NULL DEFAULT 0,
    avatar VARCHAR(255) DEFAULT NULL,
    country VARCHAR(10) DEFAULT NULL,
    badge VARCHAR(50) DEFAULT NULL,
    contests_rated INT NOT NULL DEFAULT 0,
    last_contest_id VARCHAR(40) DEFAULT NULL,
    max_rating_title ENUM('NEWBIE', 'PUPIL', 'SPECIALIST', 'EXPERT', 'CANDIDATE_MASTER', 'MASTER',
        'INTERNATIONAL_MASTER', 'GRANDMASTER', 'INTERNATIONAL_GRANDMASTER', 'LEGENDARY_GRANDMASTER')
        NOT NULL DEFAULT 'NEWBIE',
    rating_title ENUM('NEWBIE', 'PUPIL', 'SPECIALIST', 'EXPERT', 'CANDIDATE_MASTER', 'MASTER',
        'INTERNATIONAL_MASTER', 'GRANDMASTER', 'INTERNATIONAL_GRANDMASTER', 'LEGENDARY_GRANDMASTER')
        NOT NULL DEFAULT 'NEWBIE',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY global_rankings_user_id_key (user_id),
    KEY global_rankings_global_rank_idx (global_rank),
    KEY global_rankings_rating_idx (rating),
    KEY global_rankings_country_global_rank_idx (country, global_rank),
    KEY idx_global_rankings_user_id_rating (user_id, rating),
    KEY idx_global_rankings_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_analytics (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    total_registered INT NOT NULL DEFAULT 0,
    total_participated INT NOT NULL DEFAULT 0,
    completion_rate DOUBLE NOT NULL DEFAULT 0,
    problem_stats JSON DEFAULT NULL,
    score_distribution JSON DEFAULT NULL,
    time_distribution JSON DEFAULT NULL,
    top_users JSON DEFAULT NULL,
    generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY contest_analytics_contest_id_key (contest_id),
    CONSTRAINT fk_app_contest_analytics_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_announcements (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_pinned TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY contest_announcements_contest_id_created_at_idx (contest_id, created_at),
    CONSTRAINT fk_app_contest_announcements_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_rating_calculations (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    calculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_contest_rating_calculations_contest_id (contest_id),
    CONSTRAINT fk_app_contest_rating_calculations_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contest_adjudication_receipts (
    id VARCHAR(40) NOT NULL,
    submission_id VARCHAR(40) NOT NULL,
    generation BIGINT NOT NULL,
    verdict VARCHAR(30) NOT NULL,
    is_accepted TINYINT(1) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uniq_contest_adjudication_receipt (submission_id, generation),
    CONSTRAINT fk_app_contest_adjudication_receipts_submission
        FOREIGN KEY (submission_id) REFERENCES contest_submissions (submission_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
