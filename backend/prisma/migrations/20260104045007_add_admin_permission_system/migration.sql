-- AlterTable
ALTER TABLE `contests` ADD COLUMN `deleted_at` DATETIME(3) NULL,
    ADD COLUMN `deleted_by` VARCHAR(40) NULL,
    ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT false;

-- AlterTable
ALTER TABLE `forum_posts` ADD COLUMN `deleted_at` DATETIME(3) NULL,
    ADD COLUMN `deleted_by` VARCHAR(40) NULL,
    ADD COLUMN `flagged_at` DATETIME(3) NULL,
    ADD COLUMN `flagged_reason` TEXT NULL,
    ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_flagged` BOOLEAN NOT NULL DEFAULT false;

-- AlterTable
ALTER TABLE `problems` ADD COLUMN `deleted_at` DATETIME(3) NULL,
    ADD COLUMN `deleted_by` VARCHAR(40) NULL,
    ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_published` BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN `published_at` DATETIME(3) NULL,
    ADD COLUMN `published_by` VARCHAR(40) NULL;

-- AlterTable
ALTER TABLE `solutions` ADD COLUMN `deleted_at` DATETIME(3) NULL,
    ADD COLUMN `deleted_by` VARCHAR(40) NULL,
    ADD COLUMN `flagged_at` DATETIME(3) NULL,
    ADD COLUMN `flagged_reason` TEXT NULL,
    ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_flagged` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_published` BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN `published_at` DATETIME(3) NULL,
    ADD COLUMN `published_by` VARCHAR(40) NULL;

-- AlterTable
ALTER TABLE `users` ADD COLUMN `banned_reason` TEXT NULL,
    ADD COLUMN `banned_until` DATETIME(3) NULL,
    ADD COLUMN `created_by` VARCHAR(40) NULL,
    ADD COLUMN `is_active` BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN `is_banned` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `last_login_at` DATETIME(3) NULL,
    ADD COLUMN `role` ENUM('USER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN') NOT NULL DEFAULT 'USER',
    ADD COLUMN `updated_by` VARCHAR(40) NULL;

-- CreateTable
CREATE TABLE `user_permissions` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `action` ENUM('CREATE', 'READ', 'UPDATE', 'DELETE', 'MODERATE', 'PUBLISH', 'MANAGE_USERS', 'MANAGE_PERMISSIONS') NOT NULL,
    `resource` ENUM('USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST', 'FORUM_COMMENT', 'SYSTEM') NOT NULL,
    `granted_by` VARCHAR(40) NOT NULL,
    `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `expires_at` DATETIME(3) NULL,

    INDEX `user_permissions_user_id_idx`(`user_id`),
    UNIQUE INDEX `user_permissions_user_id_action_resource_key`(`user_id`, `action`, `resource`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `role_permissions` (
    `id` VARCHAR(40) NOT NULL,
    `role` ENUM('USER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN') NOT NULL,
    `action` ENUM('CREATE', 'READ', 'UPDATE', 'DELETE', 'MODERATE', 'PUBLISH', 'MANAGE_USERS', 'MANAGE_PERMISSIONS') NOT NULL,
    `resource` ENUM('USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST', 'FORUM_COMMENT', 'SYSTEM') NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `role_permissions_role_idx`(`role`),
    UNIQUE INDEX `role_permissions_role_action_resource_key`(`role`, `action`, `resource`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `audit_logs` (
    `id` VARCHAR(40) NOT NULL,
    `performer_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NULL,
    `action` VARCHAR(100) NOT NULL,
    `entity_type` VARCHAR(50) NOT NULL,
    `entity_id` VARCHAR(50) NOT NULL,
    `old_values` JSON NULL,
    `new_values` JSON NULL,
    `ip_address` VARCHAR(45) NULL,
    `user_agent` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `audit_logs_performer_id_idx`(`performer_id`),
    INDEX `audit_logs_user_id_idx`(`user_id`),
    INDEX `audit_logs_entity_type_entity_id_idx`(`entity_type`, `entity_id`),
    INDEX `audit_logs_created_at_idx`(`created_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateIndex
CREATE INDEX `users_role_idx` ON `users`(`role`);

-- CreateIndex
CREATE INDEX `users_is_active_is_banned_idx` ON `users`(`is_active`, `is_banned`);

-- AddForeignKey
ALTER TABLE `user_permissions` ADD CONSTRAINT `user_permissions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `audit_logs` ADD CONSTRAINT `audit_logs_performer_id_fkey` FOREIGN KEY (`performer_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `audit_logs` ADD CONSTRAINT `audit_logs_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
