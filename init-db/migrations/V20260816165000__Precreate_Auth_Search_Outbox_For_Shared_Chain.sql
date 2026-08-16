-- V20260816165000__Precreate_Auth_Search_Outbox_For_Shared_Chain.sql
-- SEARCH-003 slice-2: shared-chain compatibility for the auth-chain migration
-- V20260816170000.
--
-- The shared Flyway location (`filesystem:migrations`) discovers owner-chain
-- directories recursively. Most owner-dir migrations are tolerated by the
-- shared chain (CREATE TABLE IF NOT EXISTS against already-existing shared
-- tables, or db-level grants that do not require the target to exist), but
-- V20260816170000 ends with a TABLE-level grant on
-- `auth`.`search_document_changed_outbox`, which only exists after the auth
-- chain runs. On a fresh database the shared chain therefore failed there.
--
-- This migration pre-creates the auth schema and outbox table (idempotent)
-- so a fresh shared-chain run can apply the auth chain directory; the auth
-- chain itself (flyway-auth.conf, defaultSchema=auth) remains the owner of
-- the table shape and re-applies V20260816170000 as a no-op against the
-- existing table. Never edit V20260816170000 (applied in real environments).

CREATE SCHEMA IF NOT EXISTS `auth`;

CREATE TABLE IF NOT EXISTS `auth`.`search_document_changed_outbox` (
  `id`                varchar(40)  NOT NULL,
  `owner`             varchar(16)  NOT NULL DEFAULT 'Auth',
  `aggregate_id`      varchar(120) NOT NULL,
  `aggregate_version` bigint       NOT NULL DEFAULT 0,
  `event_type`        varchar(64)  NOT NULL DEFAULT 'SearchDocumentChanged',
  `schema_version`    int          NOT NULL DEFAULT 1,
  `payload`           json         NOT NULL,
  `state`             varchar(16)  NOT NULL DEFAULT 'PENDING',
  `attempts`          int          NOT NULL DEFAULT 0,
  `last_error`        text         DEFAULT NULL,
  `created_at`        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at`        datetime(3)  DEFAULT NULL,
  `claim_owner`       varchar(80)  DEFAULT NULL,
  `delivered_at`      datetime(3)  DEFAULT NULL,
  `next_retry_at`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
