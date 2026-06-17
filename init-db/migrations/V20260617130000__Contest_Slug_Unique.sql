-- Contest slug UNIQUE constraint hardening (R1, P0-5).
-- Eliminates URL/identifier ambiguity from duplicate slugs.
--
-- Step 1: dedupe existing rows that share the same slug (excluding soft-deleted).
--         We keep the row with the earliest id (lexical min, stable) and append
--         a short id-derived suffix to all others. The MIN(id) chosen by row scan
--         is deterministic because ids are UUIDs assigned at insert time.
-- Step 2: add UNIQUE index uk_contest_slug on contests(slug).
--
-- This migration is safe to run on existing data because the dedupe UPDATE
-- is self-constrained to the conflict set. No rows are lost; only the
-- slugs of later-inserted duplicates are renamed. Both old and new slugs
-- remain valid identifiers; downstream URL consumers (e.g. /contest/{slug})
-- can be left to 404 cleanly if anyone still hits a renamed slug.

SET NAMES utf8mb4;

-- Step 1: dedupe
-- Self-join UPDATE on the conflict set, keep min-id, suffix the rest.
UPDATE contests c
JOIN (
    SELECT slug
    FROM contests
    WHERE is_deleted = 0
      AND slug IS NOT NULL
    GROUP BY slug
    HAVING COUNT(*) > 1
) dup ON c.slug = dup.slug
SET c.slug = CONCAT(c.slug, '-', LEFT(c.id, 8))
WHERE c.id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) AS min_id
        FROM contests
        WHERE is_deleted = 0
          AND slug IS NOT NULL
        GROUP BY slug
    ) keepers
);

-- Step 2: add UNIQUE constraint.
-- The non-unique index `contests_slug_idx` (V20260602) is replaced
-- by uk_contest_slug; MySQL will not allow two indexes on the same
-- column, so drop the old one first.
ALTER TABLE contests DROP INDEX contests_slug_idx;
ALTER TABLE contests ADD UNIQUE KEY uk_contest_slug (slug);
