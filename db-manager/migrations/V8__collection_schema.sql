SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V8__collection_schema
-- Generated from ulticode.sql
-- Tables: 2

CREATE TABLE `collection_items` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `collection_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` enum('PROBLEM','SOLUTION','FORUM_POST','PROBLEM_LIST','SOLUTION_COMMENT','FORUM_COMMENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `note` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `collection_items_collection_id_target_type_target_id_key` (`collection_id`,`target_type`,`target_id`),
  KEY `collection_items_target_type_target_id_idx` (`target_type`,`target_id`),
  CONSTRAINT `collection_items_collection_id_fkey` FOREIGN KEY (`collection_id`) REFERENCES `collections` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `collections` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `collections_user_id_name_key` (`user_id`,`name`),
  KEY `collections_user_id_idx` (`user_id`),
  KEY `collections_user_id_is_default_idx` (`user_id`,`is_default`),
  CONSTRAINT `collections_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);



-- Seed Data

-- Table: collections (6 rows)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES ('072cb070-9b2c-4e9e-9e26-0bac5076a832','user-chen','收藏夹',NULL,NULL,NULL,0,1,NOW(3),NOW(3));
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES ('1d3ff7d2-2d66-4f48-9625-b4be9e1a6978','user-yuki','收藏夹',NULL,NULL,NULL,0,1,NOW(3),NOW(3));
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES ('cat-yuki-interview','user-yuki','面试准备',NULL,NULL,NULL,1,0,NOW(3),NOW(3));
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES ('cat-yuki-weekly','user-yuki','每周练习',NULL,NULL,NULL,0,0,NOW(3),NOW(3));
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES ('d550db4c-b39c-423f-ae44-78c6d61270cc','user-alex','收藏夹',NULL,NULL,NULL,0,1,NOW(3),NOW(3));
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES ('fd4a31b5-6996-42a6-abea-06aef32270f8','u-001','收藏夹',NULL,NULL,NULL,0,1,NOW(3),NOW(3));

-- Table: collection_items (7 rows)
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('3a6a9426-8b4a-47a5-b3e1-c2e6043fee17','d550db4c-b39c-423f-ae44-78c6d61270cc','list-sliding-window','PROBLEM_LIST',0,NULL,NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('3c3632a6-227e-47bf-9b4b-7c2dcd35d7c5','cat-yuki-weekly','list-essentials','PROBLEM_LIST',0,NULL,NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('4f7022b6-e567-4b8a-a3ed-ad2d1e85da27','072cb070-9b2c-4e9e-9e26-0bac5076a832','list-essentials','PROBLEM_LIST',0,NULL,NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('5e912cdd-9319-483d-ac21-62b9277345bf','1d3ff7d2-2d66-4f48-9625-b4be9e1a6978','list-essentials','PROBLEM_LIST',0,NULL,NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('62107a6d-bfe8-45e7-9b76-6b108728e6d0','fd4a31b5-6996-42a6-abea-06aef32270f8','list-sliding-window','PROBLEM_LIST',0,NULL,NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('8f5b82b3-bc9a-4780-8245-46c8a85fd2f8','cat-yuki-interview','list-intervals','PROBLEM_LIST',0,NULL,NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES ('dea93068-8775-47e1-9e72-003809f709c6','1d3ff7d2-2d66-4f48-9625-b4be9e1a6978','list-intervals','PROBLEM_LIST',0,NULL,NOW(3));
SET FOREIGN_KEY_CHECKS=1;
