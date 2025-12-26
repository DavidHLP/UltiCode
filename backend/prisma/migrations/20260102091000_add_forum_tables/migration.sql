-- Add missing forum tables and columns to align with Prisma schema

CREATE TABLE `forum_tags` (
    `id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(60) NOT NULL,
    `slug` VARCHAR(60) NOT NULL,
    `description` TEXT NULL,
    `color` VARCHAR(20) NULL,
    `usage_count` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `forum_tags_name_key`(`name`),
    UNIQUE INDEX `forum_tags_slug_key`(`slug`),
    INDEX `forum_tags_slug_idx`(`slug`),
    INDEX `forum_tags_usage_count_idx`(`usage_count`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `forum_post_tag_relations` (
    `post_id` VARCHAR(40) NOT NULL,
    `tag_id` VARCHAR(40) NOT NULL,

    INDEX `forum_post_tag_relations_tag_id_idx`(`tag_id`),
    PRIMARY KEY (`post_id`, `tag_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `forum_community_tags` (
    `community_id` VARCHAR(40) NOT NULL,
    `tag_id` VARCHAR(40) NOT NULL,
    `is_featured` BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY (`community_id`, `tag_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `forum_community_rules` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(120) NOT NULL,
    `body` TEXT NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `forum_community_rules_community_id_sort_order_idx`(`community_id`, `sort_order`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `forum_community_links` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `label` VARCHAR(120) NOT NULL,
    `url` VARCHAR(255) NOT NULL,
    `description` TEXT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,

    INDEX `forum_community_links_community_id_sort_order_idx`(`community_id`, `sort_order`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `forum_community_members` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `role` ENUM('OWNER', 'MODERATOR', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `forum_community_members_community_id_user_id_key`(`community_id`, `user_id`),
    INDEX `forum_community_members_user_id_idx`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `forum_community_permissions` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `role` ENUM('OWNER', 'MODERATOR', 'MEMBER') NOT NULL,
    `can_post` BOOLEAN NOT NULL DEFAULT true,
    `can_comment` BOOLEAN NOT NULL DEFAULT true,
    `can_moderate` BOOLEAN NOT NULL DEFAULT false,
    `can_manage` BOOLEAN NOT NULL DEFAULT false,

    UNIQUE INDEX `forum_community_permissions_community_id_role_key`(`community_id`, `role`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `forum_post_tag_relations`
    ADD CONSTRAINT `forum_post_tag_relations_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT `forum_post_tag_relations_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `forum_tags`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `forum_community_tags`
    ADD CONSTRAINT `forum_community_tags_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT `forum_community_tags_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `forum_tags`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `forum_community_rules`
    ADD CONSTRAINT `forum_community_rules_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `forum_community_links`
    ADD CONSTRAINT `forum_community_links_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `forum_community_members`
    ADD CONSTRAINT `forum_community_members_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `forum_community_permissions`
    ADD CONSTRAINT `forum_community_permissions_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
