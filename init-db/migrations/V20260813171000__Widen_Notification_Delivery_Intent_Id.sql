-- NOTIFY-002: stable intent ids include source-specific fields and may exceed
-- the original VARCHAR(64) ledger bound. Keep the natural id intact so replay
-- and generation-aware idempotency remain exact rather than hash-only.
ALTER TABLE notification_delivery_ledger
    MODIFY COLUMN intent_id VARCHAR(255) NOT NULL;
