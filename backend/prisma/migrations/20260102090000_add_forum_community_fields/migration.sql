-- Add missing forum_communities fields to align with schema
ALTER TABLE `forum_communities`
    ADD COLUMN `icon` VARCHAR(255) NULL,
    ADD COLUMN `color` VARCHAR(20) NULL,
    ADD COLUMN `banner` VARCHAR(255) NULL,
    ADD COLUMN `posts_count` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `posts_today` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `posts_week` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `is_official` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_featured` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `visibility` ENUM('PUBLIC', 'RESTRICTED', 'PRIVATE') NOT NULL DEFAULT 'PUBLIC';

CREATE UNIQUE INDEX `forum_communities_slug_key` ON `forum_communities`(`slug`);
CREATE INDEX `forum_communities_visibility_is_featured_idx` ON `forum_communities`(`visibility`, `is_featured`);
