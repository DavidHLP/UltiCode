-- Create additive `oauth_provider_identities` table.
-- ------------------------------------------------------------
-- Background: PROJECT_DOCUMENTATION.md §7.1 (P0-SEC-002):
-- "Provider identity table or columns capture (provider, provider_user_id)
-- separately from users.email." Currently the OAuth auto-link path uses
-- `findByOAuthEmail`, which conflates identity with email and is the
-- canonical R4 wrong-account-merge vector. This table separates the two:
-- a row in `oauth_provider_identities` is the only authoritative link
-- between an UltiCode account (auth.users.id) and a provider's stable
-- user id (provider + provider_user_id).
--
-- Schema decisions:
--   * (provider, provider_user_id) is UNIQUE — one provider identity
--     binds to at most one UltiCode account. Re-link requires an
--     explicit unlink first; this closes the "OAuth account was just
--     stolen from a different UltiCode user" attack.
--   * account_id is NOT a hard FK to users.id. Phase 5 will split the
--     `users` table by Owner (auth vs. app user_profiles); adding the
--     FK now would block that move. Application layer enforces.
--   * linked_at is auto-set; unlinked_at is null while active. Soft-
--     delete pattern supports re-link and audit without DROP.
--   * No FK to users because the cross-schema split is part of Phase 5
--     (P5-SCHEMA-001 / P5-USERPROFILE-001). Adding a FK to a column
--     that will move schemas later would force a Phase 5 contract
--     migration instead of an additive split.
--
-- Phase 0 writes:
--   This migration creates the table only. Auth does NOT write to it
--   yet; the verified-email guard added in P0-SEC-002 still gates
--   auto-link, and `findByOAuthEmail` continues to be the lookup for
--   the existing user-by-email path. Phase 2 (P2-AUTH-003) wires the
--   write side: Auth's OAuth callback inserts a row into this table
--   on every verified callback, and IdentityQueryService.batchGetIdentity
--   reads from it.
--
-- Phase 0 / future evidence required before any DROP:
--   None — this is purely additive, no existing table modified.
--
-- Rollback:
--   DROP TABLE oauth_provider_identities;
--   The migration is purely additive; rollback is safe even if writes
--   have occurred (rows are isolated to this table).
-- ------------------------------------------------------------

CREATE TABLE `oauth_provider_identities` (
  `id` varchar(40) NOT NULL,
  `account_id` varchar(40) NOT NULL,
  `provider` varchar(32) NOT NULL,
  `provider_user_id` varchar(128) NOT NULL,
  `linked_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `unlinked_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_provider_identity` (`provider`, `provider_user_id`),
  KEY `idx_oauth_provider_account` (`account_id`),
  KEY `idx_oauth_provider_unlinked` (`provider`, `unlinked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;