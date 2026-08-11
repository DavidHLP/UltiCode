-- CONTEST-004: make contest admission explicit and participant identity durable.
--
-- Real registration is one row per (contest_id, user_id), regardless of the
-- participant's later status. Virtual replay rows may be repeated after a
-- session finishes, but only one STARTED virtual session may exist for a
-- (contest_id, user_id) pair.
--
-- The audit queries intentionally run before the unique-key changes. If any
-- query returns rows, stop and reconcile those historical rows (including
-- child contest_submissions / result references) before rerunning Flyway.
-- Do not silently delete or merge scored participant history.

SET NAMES utf8mb4;

-- Historical real-registration duplicates must be reconciled first.
SELECT contest_id, user_id, COUNT(*) AS duplicate_count
FROM contest_participants
WHERE is_virtual = 0
GROUP BY contest_id, user_id
HAVING COUNT(*) > 1;

-- Historical concurrent virtual-start duplicates must be reconciled first.
SELECT contest_id, user_id, COUNT(*) AS duplicate_count
FROM contest_participants
WHERE is_virtual = 1
  AND status = 'STARTED'
GROUP BY contest_id, user_id
HAVING COUNT(*) > 1;

-- A submission may belong to one contest only. Existing duplicates need
-- explicit history reconciliation before this unique key can be added.
SELECT submission_id, COUNT(*) AS duplicate_count
FROM contest_submissions
GROUP BY submission_id
HAVING COUNT(*) > 1;

-- MySQL 8 has no partial unique indexes. Generated keys make the guarded
-- identities non-NULL only for rows that must collide.
ALTER TABLE contest_participants
    ADD COLUMN real_registration_key VARCHAR(81)
        GENERATED ALWAYS AS (
            CASE WHEN is_virtual = 0
                 THEN CONCAT(contest_id, ':', user_id)
                 ELSE NULL
            END
        ) VIRTUAL,
    ADD UNIQUE KEY uk_real_registration (real_registration_key),
    ADD COLUMN virtual_active_key VARCHAR(81)
        GENERATED ALWAYS AS (
            CASE WHEN is_virtual = 1 AND status = 'STARTED'
                 THEN CONCAT(contest_id, ':', user_id)
                 ELSE NULL
            END
        ) VIRTUAL,
    ADD UNIQUE KEY uk_virtual_active_admission (virtual_active_key);

ALTER TABLE contest_submissions
    ADD UNIQUE KEY uk_contest_submission_submission_id (submission_id);
