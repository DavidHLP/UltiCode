-- Add missing fields to subscriptions table
-- Migration timestamp: 20260328000000

-- Add transaction_id column for payment tracking
ALTER TABLE `subscriptions` ADD COLUMN `transaction_id` VARCHAR(100) NULL COMMENT 'Payment transaction ID' AFTER `cancelled_at`;

-- Add auto_renew column for subscription renewal
ALTER TABLE `subscriptions` ADD COLUMN `auto_renew` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Auto-renewal flag' AFTER `transaction_id`;

-- Add is_deleted column for soft delete (MyBatis-Plus @TableLogic)
ALTER TABLE `subscriptions` ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Soft delete flag' AFTER `auto_renew`;

-- Add deleted_at column for soft delete timestamp
ALTER TABLE `subscriptions` ADD COLUMN `deleted_at` DATETIME(3) NULL COMMENT 'Soft delete timestamp' AFTER `is_deleted`;

-- Add index on is_deleted for performance
CREATE INDEX `subscriptions_is_deleted_idx` ON `subscriptions`(`is_deleted`);
