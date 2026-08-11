-- CONTEST-006: make contest-owned relations reject new orphans.
--
-- Contest deletion remains an explicit owner transaction because the parent is
-- soft-deleted. These constraints are a second line of defence for hard
-- deletes and direct writes; they do not cross into Auth or Problem ownership.
-- The orphan audit is intentionally visible before ALTER TABLE fails, so a
-- deployment with historical orphan rows stops for reconciliation.

SELECT 'contest_analytics' AS relation, ca.contest_id
FROM contest_analytics ca
LEFT JOIN contests c ON c.id = ca.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_announcements', ca.contest_id
FROM contest_announcements ca
LEFT JOIN contests c ON c.id = ca.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_participants', cp.contest_id
FROM contest_participants cp
LEFT JOIN contests c ON c.id = cp.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_problem_results', cpr.contest_id
FROM contest_problem_results cpr
LEFT JOIN contests c ON c.id = cpr.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_problems', cp.contest_id
FROM contest_problems cp
LEFT JOIN contests c ON c.id = cp.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_rankings', cr.contest_id
FROM contest_rankings cr
LEFT JOIN contests c ON c.id = cr.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_submissions', cs.contest_id
FROM contest_submissions cs
LEFT JOIN contests c ON c.id = cs.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'first_solve_records', fsr.contest_id
FROM first_solve_records fsr
LEFT JOIN contests c ON c.id = fsr.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_rating_calculations', crc.contest_id
FROM contest_rating_calculations crc
LEFT JOIN contests c ON c.id = crc.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'virtual_contest_sessions', vcs.contest_id
FROM virtual_contest_sessions vcs
LEFT JOIN contests c ON c.id = vcs.contest_id
WHERE c.id IS NULL
UNION ALL
SELECT 'contest_adjudication_receipts', car.submission_id
FROM contest_adjudication_receipts car
LEFT JOIN contest_submissions cs ON cs.submission_id = car.submission_id
WHERE cs.submission_id IS NULL
UNION ALL
SELECT 'contest_submissions_problem', cs.id
FROM contest_submissions cs
LEFT JOIN contest_problems cp
    ON cp.id = cs.contest_problem_id AND cp.contest_id = cs.contest_id
WHERE cp.id IS NULL
UNION ALL
SELECT 'contest_submissions_participant', cs.id
FROM contest_submissions cs
LEFT JOIN contest_participants cp
    ON cp.id = cs.participant_id AND cp.contest_id = cs.contest_id
WHERE cp.id IS NULL
UNION ALL
SELECT 'contest_problem_results_problem', cpr.id
FROM contest_problem_results cpr
LEFT JOIN contest_problems cp
    ON cp.id = cpr.contest_problem_id AND cp.contest_id = cpr.contest_id
WHERE cp.id IS NULL
UNION ALL
SELECT 'contest_problem_results_participant', cpr.id
FROM contest_problem_results cpr
LEFT JOIN contest_participants cp
    ON cp.id = cpr.participant_id AND cp.contest_id = cpr.contest_id
WHERE cp.id IS NULL
UNION ALL
SELECT 'contest_problem_results_ranking', cpr.id
FROM contest_problem_results cpr
LEFT JOIN contest_rankings cr
    ON cr.id = cpr.ranking_id AND cr.contest_id = cpr.contest_id
WHERE cpr.ranking_id IS NOT NULL AND cr.id IS NULL;

DROP PROCEDURE IF EXISTS `_add_contest_relational_guards`;
DELIMITER //
CREATE PROCEDURE `_add_contest_relational_guards`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'contest_participants'
          AND index_name = 'uk_contest_participants_id_contest'
    ) THEN
        ALTER TABLE contest_participants
            ADD UNIQUE KEY uk_contest_participants_id_contest (id, contest_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_analytics'
          AND constraint_name = 'fk_contest_analytics_contest'
    ) THEN
        ALTER TABLE contest_analytics
            ADD CONSTRAINT fk_contest_analytics_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_announcements'
          AND constraint_name = 'fk_contest_announcements_contest'
    ) THEN
        ALTER TABLE contest_announcements
            ADD CONSTRAINT fk_contest_announcements_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_participants'
          AND constraint_name = 'fk_contest_participants_contest'
    ) THEN
        ALTER TABLE contest_participants
            ADD CONSTRAINT fk_contest_participants_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'contest_problems'
          AND index_name = 'uk_contest_problems_id_contest'
    ) THEN
        ALTER TABLE contest_problems
            ADD UNIQUE KEY uk_contest_problems_id_contest (id, contest_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_problems'
          AND constraint_name = 'fk_contest_problems_contest'
    ) THEN
        ALTER TABLE contest_problems
            ADD CONSTRAINT fk_contest_problems_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'contest_rankings'
          AND index_name = 'uk_contest_rankings_id_contest'
    ) THEN
        ALTER TABLE contest_rankings
            ADD UNIQUE KEY uk_contest_rankings_id_contest (id, contest_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_rankings'
          AND constraint_name = 'fk_contest_rankings_contest'
    ) THEN
        ALTER TABLE contest_rankings
            ADD CONSTRAINT fk_contest_rankings_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'virtual_contest_sessions'
          AND index_name = 'uk_virtual_contest_sessions_id_contest'
    ) THEN
        ALTER TABLE virtual_contest_sessions
            ADD UNIQUE KEY uk_virtual_contest_sessions_id_contest (id, contest_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'virtual_contest_sessions'
          AND constraint_name = 'fk_virtual_contest_sessions_contest'
    ) THEN
        ALTER TABLE virtual_contest_sessions
            ADD CONSTRAINT fk_virtual_contest_sessions_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_submissions'
          AND constraint_name = 'fk_contest_submissions_contest'
    ) THEN
        ALTER TABLE contest_submissions
            ADD CONSTRAINT fk_contest_submissions_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_submissions'
          AND constraint_name = 'fk_contest_submissions_problem_contest'
    ) THEN
        ALTER TABLE contest_submissions
            ADD CONSTRAINT fk_contest_submissions_problem_contest
            FOREIGN KEY (contest_problem_id, contest_id)
            REFERENCES contest_problems (id, contest_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_submissions'
          AND constraint_name = 'fk_contest_submissions_participant_contest'
    ) THEN
        ALTER TABLE contest_submissions
            ADD CONSTRAINT fk_contest_submissions_participant_contest
            FOREIGN KEY (participant_id, contest_id)
            REFERENCES contest_participants (id, contest_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_problem_results'
          AND constraint_name = 'fk_contest_problem_results_contest'
    ) THEN
        ALTER TABLE contest_problem_results
            ADD CONSTRAINT fk_contest_problem_results_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_problem_results'
          AND constraint_name = 'fk_contest_problem_results_problem_contest'
    ) THEN
        ALTER TABLE contest_problem_results
            ADD CONSTRAINT fk_contest_problem_results_problem_contest
            FOREIGN KEY (contest_problem_id, contest_id)
            REFERENCES contest_problems (id, contest_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_problem_results'
          AND constraint_name = 'fk_contest_problem_results_participant_contest'
    ) THEN
        ALTER TABLE contest_problem_results
            ADD CONSTRAINT fk_contest_problem_results_participant_contest
            FOREIGN KEY (participant_id, contest_id)
            REFERENCES contest_participants (id, contest_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_problem_results'
          AND constraint_name = 'fk_contest_problem_results_ranking_contest'
    ) THEN
        ALTER TABLE contest_problem_results
            ADD CONSTRAINT fk_contest_problem_results_ranking_contest
            FOREIGN KEY (ranking_id, contest_id)
            REFERENCES contest_rankings (id, contest_id) ON DELETE RESTRICT;
    END IF;

    -- A foreign key may reference only a candidate key. The legacy table
    -- had a non-unique lookup index, but one submission belongs to one contest
    -- row, so make that invariant explicit before adding the receipt FK.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_submissions'
          AND constraint_name = 'uk_contest_submissions_submission_id'
    ) THEN
        ALTER TABLE contest_submissions
            ADD UNIQUE KEY uk_contest_submissions_submission_id (submission_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_adjudication_receipts'
          AND constraint_name = 'fk_contest_adjudication_receipts_submission'
    ) THEN
        ALTER TABLE contest_adjudication_receipts
            ADD CONSTRAINT fk_contest_adjudication_receipts_submission
            FOREIGN KEY (submission_id) REFERENCES contest_submissions (submission_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'first_solve_records'
          AND constraint_name = 'fk_first_solve_records_contest'
    ) THEN
        ALTER TABLE first_solve_records
            ADD CONSTRAINT fk_first_solve_records_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'contest_rating_calculations'
          AND constraint_name = 'fk_contest_rating_calculations_contest'
    ) THEN
        ALTER TABLE contest_rating_calculations
            ADD CONSTRAINT fk_contest_rating_calculations_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE RESTRICT;
    END IF;
END //
DELIMITER ;
CALL `_add_contest_relational_guards`();
DROP PROCEDURE IF EXISTS `_add_contest_relational_guards`;
