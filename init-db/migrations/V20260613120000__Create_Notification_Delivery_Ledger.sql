-- ADR-004 M4a: Notification delivery ledger.
-- Reference: wiki/concepts/notification-dispatch-and-preferences.md §2.2 (typed dispatcher), §2.3 (per-channel ledger), §2.7 (F9 idempotency 修订).
-- Per (intent_id, channel_id) UNIQUE = physical idempotency even on multi-replica
-- or pm2 reload. tryClaim() uses INSERT ... ON DUPLICATE KEY UPDATE id=id so the
-- claim itself is atomic; subsequent markDelivered / markFailed transitions the
-- delivery_state. The table is append-only (no logical delete) and intentionally
-- has no FK to the business Notification table — it is a delivery audit trail,
-- not a referential mirror of business state.
-- Updated_at mirrors the row's state transition (DELIVERED / FAILED), not the
-- original intent creation time (see `delivered_at` for the latter).
CREATE TABLE notification_delivery_ledger (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  intent_id       VARCHAR(64)     NOT NULL                COMMENT 'NotificationIntent.intentId(); stable across retries',
  channel_id      VARCHAR(32)     NOT NULL                COMMENT '"in_app" / "email" / "websocket"',
  user_id         VARCHAR(36)     NOT NULL                COMMENT 'recipient — denormalized for ops queries',
  intent_type     VARCHAR(64)     NOT NULL                COMMENT 'record class simpleName (SubmissionCompletedIntent, ...)',
  delivery_state  VARCHAR(16)     NOT NULL                COMMENT 'CLAIMED / DELIVERED / SKIPPED / FAILED',
  failure_reason  VARCHAR(500)    NULL                    COMMENT 'truncated error message on FAILED; null otherwise',
  delivered_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT 'row creation = ledger claim time',
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)  COMMENT 'state transition time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_delivery_ledger_intent_channel (intent_id, channel_id),
  KEY idx_notification_delivery_ledger_user_time (user_id, delivered_at),
  KEY idx_notification_delivery_ledger_state (delivery_state, delivered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
