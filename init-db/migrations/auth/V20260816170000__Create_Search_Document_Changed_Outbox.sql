-- SEARCH-001 slice-b: durable outbox for user-document index changes.
-- Auth is the owner of the `users` row; App owns `user_profiles`. Auth writes
-- this outbox inside the same transaction as the users write; the Auth
-- dispatcher XADDs each row to `stream:integration` (DEC-014 pattern) so the
-- future backend-search worker never reads a business table.
--
-- The payload is the full SearchDocumentChanged event payload
-- (index/operation/document/occurredAt); document=null means DELETE tombstone.
-- `auth_rw` holds table-scoped DML so the runtime account can enqueue; claim/
-- delivery is done by the in-process dispatcher using the same account.

CREATE TABLE IF NOT EXISTS `search_document_changed_outbox` (
  `id`              varchar(40)   NOT NULL,
  `owner`           varchar(16)   NOT NULL DEFAULT 'Auth',
  `aggregate_id`    varchar(120)  NOT NULL,
  `aggregate_version` bigint      NOT NULL DEFAULT 0,
  `event_type`      varchar(64)   NOT NULL DEFAULT 'SearchDocumentChanged',
  `schema_version`  int           NOT NULL DEFAULT 1,
  `payload`         json          NOT NULL,
  `state`           varchar(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING / CLAIMED / DELIVERED / FAILED
  `attempts`        int           NOT NULL DEFAULT 0,
  `last_error`      text          DEFAULT NULL,
  `created_at`      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at`      datetime(3)   DEFAULT NULL,
  `claim_owner`     varchar(80)   DEFAULT NULL,
  `delivered_at`    datetime(3)   DEFAULT NULL,
  `next_retry_at`   datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

GRANT SELECT, INSERT, UPDATE, DELETE ON `auth`.`search_document_changed_outbox` TO 'auth_rw'@'%';
FLUSH PRIVILEGES;
