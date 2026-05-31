-- =============================================================================
-- Seed test data for contest management testing
-- Creates 5 contests with realistic participant data linked to real users
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. CONTESTS (5 records: ICPC, IOI, Custom x various statuses)
-- -----------------------------------------------------------------------------
INSERT INTO contests (
  id, title, slug, contest_type, start_time, end_time, duration_minutes,
  status, penalty_per_wrong, scoring_mode, tie_breaker,
  registered_count, participant_count, submission_count, is_rated,
  description, created_by, is_visible, rules,
  created_at, updated_at, is_deleted, is_virtual, max_participants,
  registration_start, registration_end
) VALUES
-- Past Contest Archive (FINISHED, 6 participants, all real users)
('c-archive-001', 'Past Contest Archive', 'past-contest-archive', 'CUSTOM',
 DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 2 HOUR),
 120, 'FINISHED', 0, 'SCORE', 'NONE',
 6, 6, 38, 0,
 'Archived contest for practice. All submissions evaluated but do not affect ratings.',
 'u-admin-001', 1,
 'Practice mode: Submissions judged but do not affect ratings.',
 NOW(), NOW(), 0, 0, NULL,
 DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),

-- Quick Fire Round (RUNNING, 4 participants in progress)
('c-quick-fire-01', 'Quick Fire Round', 'quick-fire-round', 'CUSTOM',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 30 MINUTE),
 60, 'RUNNING', 0, 'SCORE', 'TOTAL_ATTEMPTS',
 4, 4, 12, 0,
 'Fast-paced coding challenge. 10 problems in 60 minutes. Score-based with time bonus.',
 'u-admin-001', 1,
 'Quick Fire Rules: 1. 10 problems of increasing difficulty. 2. Base score plus time bonus. 3. No penalties.',
 NOW(), NOW(), 0, 0, 100, NULL, NULL),

-- Weekly Programming Challenge #42 (UPCOMING, 2 registered)
('c-weekly-042', 'Weekly Programming Challenge #42', 'weekly-programming-challenge-42', 'ICPC',
 DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 7 DAY), INTERVAL 2 HOUR),
 120, 'UPCOMING', 300, 'ICPC', 'LAST_SOLVE_TIME',
 2, 0, 0, 1,
 'Weekly ICPC-style programming contest. Solve as many problems as you can with minimum penalty time.',
 'u-admin-001', 1,
 'ICPC Rules: 1. Each wrong submission adds 20 minutes penalty. 2. Solutions judged as Accepted or Rejected. 3. Rankings by problems solved then by penalty time.',
 NOW(), NOW(), 0, 0, 100,
 DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY)),

-- IOI Style Score Contest (DRAFT, no registrations)
('c-ioi-score-01', 'IOI Style Score Contest', 'ioi-style-score-contest', 'IOI',
 DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 14 DAY), INTERVAL 3 HOUR),
 180, 'DRAFT', 0, 'IOI', 'NONE',
 0, 0, 0, 0,
 'IOI-style contest with score-based scoring. Each problem has multiple test cases with partial scoring.',
 'u-admin-001', 0,
 'IOI Rules: 1. Each problem has a maximum score. 2. Partial credit for passing test cases. 3. No penalty for wrong submissions.',
 NOW(), NOW(), 0, 0, 50,
 DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 13 DAY)),

-- ICPC Regional Warmup (UPCOMING, 4 registered)
('c-regional-warmup', 'ICPC Regional Warmup', 'icpc-regional-warmup', 'ICPC',
 DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 30 DAY), INTERVAL 4 HOUR),
 240, 'UPCOMING', 300, 'ICPC', 'LAST_SOLVE_TIME',
 4, 0, 0, 1,
 'Practice contest for upcoming ICPC regionals. Full rules with scoreboard freeze.',
 'u-admin-001', 1,
 'Standard ICPC regional rules apply. Scoreboard freezes for last hour.',
 NOW(), NOW(), 0, 0, 200,
 DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 28 DAY));

-- -----------------------------------------------------------------------------
-- 2. CONTEST PROBLEMS (1 problem per contest, linked to problem id=1)
-- -----------------------------------------------------------------------------
INSERT INTO contest_problems (id, contest_id, problem_id, problem_index, score, penalty_per_wrong, solved_count, submission_count, label, base_score, time_bonus, created_at, updated_at) VALUES
('cp-arc-a', 'c-archive-001', 1, 'A', 100, 0, 5, 8, 'Easy', 100, 1, NOW(), NOW()),
('cp-qf-a', 'c-quick-fire-01', 1, 'A', 50, 0, 3, 6, 'Easy', 50, 2, NOW(), NOW()),
('cp-w042-a', 'c-weekly-042', 1, 'A', 100, 300, 0, 0, 'Warmup', 100, 1, NOW(), NOW()),
('cp-reg-a', 'c-regional-warmup', 1, 'A', 100, 300, 0, 0, 'Warmup', 100, 1, NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 3. CONTEST PARTICIPANTS (16 records linked to real users)
-- -----------------------------------------------------------------------------

-- Past Contest Archive: 6 participants (all real users, all FINISHED)
INSERT INTO contest_participants (id, contest_id, user_id, status, registered_at, started_at, finished_at, is_virtual, final_rank, total_penalty, total_score, total_attempts, last_solve_time, total_time, attempt_count, created_at, updated_at) VALUES
('cpart-arc-01', 'c-archive-001', 'u-admin-001', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 95 MINUTE),
 0, 1, 600, 300, 8, 5700, 5700, 8, NOW(), NOW()),
('cpart-arc-02', 'c-archive-001', 'user-alice-001', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 100 MINUTE),
 0, 2, 900, 250, 10, 6000, 6000, 10, NOW(), NOW()),
('cpart-arc-03', 'c-archive-001', 'user-bob-002', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 105 MINUTE),
 0, 3, 1200, 200, 12, 6300, 6300, 12, NOW(), NOW()),
('cpart-arc-04', 'c-archive-001', 'user-carol-003', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 110 MINUTE),
 0, 4, 1500, 150, 6, 6600, 6600, 6, NOW(), NOW()),
('cpart-arc-05', 'c-archive-001', 'user-david-004', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 115 MINUTE),
 0, 5, 1800, 100, 4, 6900, 6900, 4, NOW(), NOW()),
('cpart-arc-06', 'c-archive-001', 'user-eva-005', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 120 MINUTE),
 0, 6, 2100, 50, 3, 7200, 7200, 3, NOW(), NOW()),

-- Quick Fire Round: 4 participants (STARTED, in progress)
('cpart-qf-01', 'c-quick-fire-01', 'user-alice-001', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL,
 0, NULL, 0, 80, 4, NULL, 30, 4, NOW(), NOW()),
('cpart-qf-02', 'c-quick-fire-01', 'user-bob-002', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL,
 0, NULL, 0, 60, 3, NULL, 30, 3, NOW(), NOW()),
('cpart-qf-03', 'c-quick-fire-01', 'user-carol-003', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 28 MINUTE), DATE_SUB(NOW(), INTERVAL 28 MINUTE), NULL,
 0, NULL, 0, 40, 3, NULL, 28, 3, NOW(), NOW()),
('cpart-qf-04', 'c-quick-fire-01', 'user-david-004', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 25 MINUTE), DATE_SUB(NOW(), INTERVAL 25 MINUTE), NULL,
 0, NULL, 0, 20, 2, NULL, 25, 2, NOW(), NOW()),

-- Weekly Programming Challenge #42: 2 users REGISTERED
('cpart-w042-01', 'c-weekly-042', 'user-alice-001', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-w042-02', 'c-weekly-042', 'user-bob-002', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),

-- ICPC Regional Warmup: 4 users REGISTERED
('cpart-reg-01', 'c-regional-warmup', 'u-admin-001', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-reg-02', 'c-regional-warmup', 'user-alice-001', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-reg-03', 'c-regional-warmup', 'user-eva-005', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-reg-04', 'c-regional-warmup', 'user-frank-006', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 4. CONTEST RANKINGS (for finished contest)
-- -----------------------------------------------------------------------------
INSERT INTO contest_rankings (id, contest_id, user_id, `rank`, rating_before, rating_after, rating_change, is_virtual, solved_count, total_penalty, total_score, finish_time, total_attempts, is_frozen) VALUES
('crank-arc-01', 'c-archive-001', 'u-admin-001', 1, 1500, 1500, 0, 0, 3, 600, 300, 5700, 8, 0),
('crank-arc-02', 'c-archive-001', 'user-alice-001', 2, 1500, 1500, 0, 0, 2, 900, 250, 6000, 10, 0),
('crank-arc-03', 'c-archive-001', 'user-bob-002', 3, 1500, 1500, 0, 0, 2, 1200, 200, 6300, 12, 0),
('crank-arc-04', 'c-archive-001', 'user-carol-003', 4, 1500, 1500, 0, 0, 1, 1500, 150, 6600, 6, 0),
('crank-arc-05', 'c-archive-001', 'user-david-004', 5, 1500, 1500, 0, 0, 1, 1800, 100, 6900, 4, 0),
('crank-arc-06', 'c-archive-001', 'user-eva-005', 6, 1500, 1500, 0, 0, 1, 2100, 50, 7200, 3, 0);