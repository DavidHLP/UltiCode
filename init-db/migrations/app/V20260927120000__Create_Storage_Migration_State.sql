-- Deploy-gate evidence for the legacy object backfill. Rewriting an avatar row
-- and re-indexing it in Meilisearch are two different states: after `--apply`
-- the rows already hold object keys, so a row predicate alone cannot tell a
-- finished migration from one whose indexed user documents still serve the
-- obsolete `/uploads/avatars/...` URLs. The operator-run migration records both
-- states here, and `scripts/runbooks/assert-legacy-objects-migrated.sh` refuses
-- a rollout while a rewrite is still unconfirmed.

CREATE TABLE IF NOT EXISTS `app`.`storage_migration_state` (
    `id` tinyint NOT NULL,
    `avatar_rows_rewritten_at` datetime(3) DEFAULT NULL,
    `users_index_backfill_confirmed_at` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
