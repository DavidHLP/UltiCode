-- C4: Prevent concurrent active virtual sessions for the same user in the same contest.
--
-- Design: a VIRTUAL generated column `active_virtual_key` holds a deterministic string
-- (contest_id + '-' + user_id) ONLY when the row represents an active virtual
-- session (is_virtual=1 AND status='STARTED').  The unique key is placed on that
-- generated column, so MySQL enforces "at most one active virtual participant per
-- user per contest" without any application-level locking or cleanup queries.
--
-- Properties:
--   - Multiple FINISHED virtual rows for the same (contest, user) are allowed:
--     their active_virtual_key is NULL, and MySQL treats NULL as distinct in a
--     unique index (NULL != NULL), so no violation.
--   - A new STARTED row is blocked if another STARTED row already occupies the slot:
--     unique key violation → DuplicateKeyException from Spring JDBC; callers handle it
--   - is_virtual is never mutated on finish, so historical queries remain correct.
--   - VIRTUAL (not STORED) avoids write amplification on status changes.
--
-- Load-bearing constraints preserved:
--   D-05/D-06  – status transition rules stay in ContestParticipantTransitions
--   R6.2/F-06  – effective time arithmetic stays in ContestClock
--   D-04       – submission intake is NOT modified by this migration
--   C4       – idempotent duplicate-key retry handled in ContestParticipationServiceImpl

-- Step 1: resolve any pre-existing duplicate active virtual participants caused
-- by prior race conditions.  Update duplicates to FINISHED instead of deleting,
-- preserving submission history.  Keep the row with the earliest effective start
-- (started_at, fallback registered_at/created_at); if same, keep the smaller id.
UPDATE contest_participants cp1
INNER JOIN contest_participants cp2
  ON cp1.contest_id = cp2.contest_id
  AND cp1.user_id = cp2.user_id
  AND cp1.is_virtual = 1 AND cp2.is_virtual = 1
  AND cp1.status = 'STARTED' AND cp2.status = 'STARTED'
  AND (COALESCE(cp1.started_at, cp1.registered_at, cp1.created_at) >
       COALESCE(cp2.started_at, cp2.registered_at, cp2.created_at)
       OR (COALESCE(cp1.started_at, cp1.registered_at, cp1.created_at) =
           COALESCE(cp2.started_at, cp2.registered_at, cp2.created_at)
           AND cp1.id > cp2.id))
SET cp1.status = 'FINISHED',
    cp1.finished_at = NOW(),
    cp1.updated_at = NOW();

-- Step 2: add the VIRTUAL generated column.  Nullable because the CASE expression
-- yields NULL for non-active rows, and MySQL requires NULL for generated columns
-- whose expression can produce NULL.  VIRTUAL avoids write amplification:
-- the value is computed on read, not stored on every UPDATE.
ALTER TABLE contest_participants
  -- VARCHAR(128): max contest_id (40) + 1 hyphen + max user_id (40) = 81, round up to 128
  ADD COLUMN active_virtual_key VARCHAR(128) GENERATED ALWAYS AS (
    CASE
      WHEN is_virtual = 1 AND status = 'STARTED'
      THEN CONCAT(contest_id, '-', user_id)
      ELSE NULL
    END
  ) VIRTUAL;

-- Step 3: add the unique key on the generated column.
-- Multiple NULLs are permitted because MySQL's unique index treats NULL as distinct.
ALTER TABLE contest_participants
  ADD CONSTRAINT uk_virtual_active UNIQUE (active_virtual_key);
