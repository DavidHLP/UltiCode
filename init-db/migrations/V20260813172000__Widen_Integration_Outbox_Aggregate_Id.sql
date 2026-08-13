-- NOTIFY-002: durable notification intent ids are used as aggregate ids
-- for the integration outbox. Preserve the natural id for replay and ordering.
ALTER TABLE `integration_outbox`
  MODIFY COLUMN `aggregate_id` varchar(255) NOT NULL COMMENT 'Root aggregate identifier';
