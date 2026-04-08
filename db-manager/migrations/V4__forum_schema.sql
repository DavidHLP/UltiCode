SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V4__forum_schema
-- Generated from ulticode.sql
-- Tables: 11

CREATE TABLE `forum_comments` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `post_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `author_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `markdown` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL,
  `edited_at` datetime(3) DEFAULT NULL,
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  `is_locked` tinyint(1) NOT NULL DEFAULT '0',
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text COLLATE utf8mb4_unicode_ci,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `forum_comments_author_id_fkey` (`author_id`),
  KEY `forum_comments_parent_id_fkey` (`parent_id`),
  KEY `forum_comments_post_id_fkey` (`post_id`),
  KEY `forum_comments_post_id_created_at_idx` (`post_id`,`created_at`),
  CONSTRAINT `forum_comments_author_id_fkey` FOREIGN KEY (`author_id`) REFERENCES `forum_users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `forum_comments_parent_id_fkey` FOREIGN KEY (`parent_id`) REFERENCES `forum_comments` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `forum_comments_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_communities` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `members` int NOT NULL DEFAULT '0',
  `online` int NOT NULL DEFAULT '0',
  `icon` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banner` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `posts_count` int NOT NULL DEFAULT '0',
  `posts_today` int NOT NULL DEFAULT '0',
  `posts_week` int NOT NULL DEFAULT '0',
  `is_official` tinyint(1) NOT NULL DEFAULT '0',
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `visibility` enum('PUBLIC','RESTRICTED','PRIVATE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLIC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_communities_slug_key` (`slug`),
  KEY `forum_communities_slug_idx` (`slug`),
  KEY `forum_communities_visibility_is_featured_idx` (`visibility`,`is_featured`)
);

CREATE TABLE `forum_community_links` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `community_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `forum_community_links_community_id_sort_order_idx` (`community_id`,`sort_order`),
  CONSTRAINT `forum_community_links_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_community_members` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `community_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('OWNER','MODERATOR','MEMBER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEMBER',
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_community_members_community_id_user_id_key` (`community_id`,`user_id`),
  KEY `forum_community_members_user_id_idx` (`user_id`),
  CONSTRAINT `forum_community_members_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_community_permissions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `community_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('OWNER','MODERATOR','MEMBER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `can_post` tinyint(1) NOT NULL DEFAULT '1',
  `can_comment` tinyint(1) NOT NULL DEFAULT '1',
  `can_moderate` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_community_permissions_community_id_role_key` (`community_id`,`role`),
  CONSTRAINT `forum_community_permissions_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_community_rules` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `community_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `forum_community_rules_community_id_sort_order_idx` (`community_id`,`sort_order`),
  CONSTRAINT `forum_community_rules_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_community_tags` (
  `community_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tag_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`community_id`,`tag_id`),
  KEY `forum_community_tags_tag_id_fkey` (`tag_id`),
  CONSTRAINT `forum_community_tags_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `forum_community_tags_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `forum_tags` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_post_tag_relations` (
  `post_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tag_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`post_id`,`tag_id`),
  KEY `forum_post_tag_relations_tag_id_idx` (`tag_id`),
  CONSTRAINT `forum_post_tag_relations_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `forum_post_tag_relations_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `forum_tags` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_posts` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `community_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permalink` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `flair_type` enum('announcement','discussion','showcase','question','hiring') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `flair_label` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags` json NOT NULL,
  `excerpt` text COLLATE utf8mb4_unicode_ci,
  `media` json DEFAULT NULL,
  `recommendation` json DEFAULT NULL,
  `vote_state` enum('upvoted','downvoted','neutral') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'neutral',
  `is_saved` tinyint(1) NOT NULL DEFAULT '0',
  `impressions` int NOT NULL DEFAULT '0',
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  `is_locked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `stats` json DEFAULT NULL,
  `views` int NOT NULL DEFAULT '0',
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text COLLATE utf8mb4_unicode_ci,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `forum_posts_community_id_fkey` (`community_id`),
  KEY `forum_posts_user_id_fkey` (`user_id`),
  KEY `forum_posts_is_deleted_created_at_idx` (`is_deleted`,`created_at`),
  KEY `forum_posts_community_id_created_at_idx` (`community_id`,`created_at`),
  CONSTRAINT `forum_posts_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `forum_posts_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `forum_users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `forum_tags` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `usage_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_tags_name_key` (`name`),
  UNIQUE KEY `forum_tags_slug_key` (`slug`),
  KEY `forum_tags_slug_idx` (`slug`),
  KEY `forum_tags_usage_count_idx` (`usage_count`)
);

CREATE TABLE `forum_users` (
  `username` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `karma` int NOT NULL DEFAULT '0',
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_users_username_key` (`username`)
);



-- Seed Data

-- Table: forum_communities (4 rows)
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-career','职业发展','career','职业建议、求职机会和职业发展讨论。',8900,220,'Briefcase','#10B981',NULL,2100,28,189,1,1,2,NOW(3),'PUBLIC');
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-compensation','薪资福利','compensation','讨论科技行业薪资、福利和薪酬方案。',15200,680,'DollarSign','#F59E0B',NULL,4850,92,541,1,1,3,NOW(3),'PUBLIC');
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-interview','面试经验','interview','分享和讨论面试经历、面试题目和备考策略。',12500,450,'MessageSquare','#3B82F6',NULL,3420,45,312,1,1,1,NOW(3),'PUBLIC');
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-technology','技术交流','technology','技术讨论、新技术、算法和最佳实践。',9800,305,'Cpu','#8B5CF6',NULL,2890,38,267,1,1,4,NOW(3),'PUBLIC');

-- Table: forum_posts (3 rows)
INSERT INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`, `vote_state`, `is_saved`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('post-contest-tilt','community-technology','user-david',NULL,'「30分钟撞墙」：比赛中如何重置心态？','discussion',NULL,'[\"mindset\", \"psychology\", \"strategy\"]','昨天 Q2 卡了 40 分钟 debug 一个 off-by-one，之后 Q3/Q4 完全无法集中，脑子像灌了浆糊。\n\n大家有没有什么物理或心理上的硬重置方法？听说过有人做俯卧撑或者泼冷水。',NULL,NULL,'neutral',0,5120,1,0,NOW(3),'{\"saves\": 0, \"views\": 0, \"awards\": 0, \"shares\": 0, \"comments\": 7}',12,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`, `vote_state`, `is_saved`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('post-rust-hashmap','community-technology','u-002',NULL,'为什么 JavaScript 竞赛中 Map 比普通对象慢？','question',NULL,'[\"typescript\", \"performance\", \"hashing\"]','最近刷 AtCoder 性能题，发现 Map 和普通对象之间有巨大的性能差距。',NULL,NULL,'neutral',0,4200,0,0,NOW(3),'{\"saves\": 0, \"views\": 0, \"awards\": 0, \"shares\": 0, \"comments\": 6}',8,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`, `vote_state`, `is_saved`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('post-segtree-visual','community-technology','user-tourist',NULL,'线段树可视化指南（懒标记传播）','showcase',NULL,'[\"tutorial\", \"segment-tree\", \"visualization\"]','写了一个交互式博客，可视化懒标记在查询时如何向下传播。\n\n[可视化链接](https://example.com/segtree-vis)\n\n核心洞察：「懒标记就是待执行的操作」。\n大部分 bug 来自：\n1. 读取子节点前没有 pushdown。\n2. 子节点返回后没有更新当前节点。\n\n觉得有帮助的话欢迎反馈！','[{\"src\": \"https://images.unsplash.com/photo-1509228468518-180dd4864904?auto=format&fit=crop&w=1200&q=80\", \"kind\": \"image\", \"type\": \"image\", \"ratio\": 1.777777777777778}]',NULL,'neutral',1,8900,0,0,NOW(3),'{\"saves\": 0, \"views\": 0, \"awards\": 0, \"shares\": 0, \"comments\": 5}',0,0,NULL,NULL,0,NULL,NULL);

-- Table: forum_comments (18 rows)
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-1','post-rust-hashmap',NULL,'user-benq','Map 有额外的哈希和装箱开销。在竞赛中，如果键是小整数，使用 null-prototype 对象或数组通常更快。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-2','post-rust-hashmap','c-rust-1','u-002','有道理。我之前以为 Map 默认最快。我试试 null-prototype 对象。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-3','post-rust-hashmap','c-rust-2','user-petr','注意对象的使用：键的字符串化或混合类型会严重影响性能。保持键类型一致。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-4','post-rust-hashmap','c-rust-3','user-yuki','JS 评测系统会包含对抗性键模式吗？还是测试用例大多是静态的？',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-5','post-rust-hashmap','c-rust-4','user-petr','是静态的，但不良的键分布仍有影响。如果键空间密集，用数组。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-6','post-rust-hashmap',NULL,'user-alex','在 JS 中，数组对于密集整数键通常最快。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-1','post-segtree-visual',NULL,'user-jiangly','可视化做得很好。第 3 页有个小笔误：「propogate」应该是「propagate」。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-2','post-segtree-visual','c-seg-1','user-tourist','已修复！感谢指正。🙏',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-3','post-segtree-visual',NULL,'user-kevin','这个支持线段树 beats 吗？',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-4','post-segtree-visual','c-seg-3','user-tourist','暂不支持。Beats 需要追踪最小值/最大值/次大值，不太容易清晰可视化。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-5','post-segtree-visual','c-seg-3','user-max','可以看看 JiDriver 的博客，有 Segment Tree Beats 的可视化。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-1','post-contest-tilt',NULL,'u-001','呼吸法：吸气 4 秒，屏息 4 秒，呼气 4 秒。重复 3 次。从生理上强制降低心率。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-2','post-contest-tilt','c-tilt-1','user-david','下次模拟赛试试。我通常就是盯着屏幕过度换气 lol。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-3','post-contest-tilt','c-tilt-2','user-lily','另外，站起来。物理上改变姿势可以重置「管状视野」。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-4','post-contest-tilt',NULL,'user-scott','我通常直接退出去打 League。（别学我）',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-5','post-contest-tilt','c-tilt-4','user-tom','笑死，上次 CF 我就是这样。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-6','post-contest-tilt',NULL,'user-sara','我喝冷水。温度刺激能唤醒前额叶皮层。',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-7','post-contest-tilt','c-tilt-6','user-emma','科学！',NULL,NOW(3),NULL,0,0,0,NULL,NULL,0,NULL,NULL);

-- Table: forum_tags (9 rows)
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-data-structures','数据结构','data-structures',NULL,'#8B5CF6',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-hashing','哈希','hashing',NULL,'#3B82F6',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-mindset','心态','mindset',NULL,'#8B5CF6',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-performance','性能','performance',NULL,'#F59E0B',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-psychology','心理学','psychology',NULL,'#EC4899',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-strategy','策略','strategy',NULL,'#10B981',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-tutorial','教程','tutorial',NULL,'#06B6D4',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-typescript','typescript','typescript',NULL,'#3178C6',1,NOW(3));
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-visualization','可视化','visualization',NULL,'#F59E0B',1,NOW(3));

-- Table: forum_post_tag_relations (9 rows)
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-rust-hashmap','tag-typescript');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-rust-hashmap','tag-performance');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-rust-hashmap','tag-hashing');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-segtree-visual','tag-tutorial');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-segtree-visual','tag-visualization');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-segtree-visual','tag-data-structures');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-contest-tilt','tag-mindset');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-contest-tilt','tag-psychology');
INSERT INTO `forum_post_tag_relations` (`post_id`, `tag_id`) VALUES ('post-contest-tilt','tag-strategy');

-- Table: forum_users (19 rows)
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('shadcn','https://api.dicebear.com/7.x/notionists/svg?seed=shadcn',0,'u-001');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('stack_unwind','https://api.dicebear.com/7.x/notionists/svg?seed=stack_unwind',0,'u-002');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('alex_algorithm','https://api.dicebear.com/7.x/notionists/svg?seed=alex',0,'user-alex');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('Benq','https://api.dicebear.com/7.x/notionists/svg?seed=benq',0,'user-benq');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('chen_master','https://api.dicebear.com/7.x/notionists/svg?seed=chen',0,'user-chen');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('david_algo','https://api.dicebear.com/7.x/notionists/svg?seed=david',0,'user-david');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('ecnerwala','https://api.dicebear.com/7.x/notionists/svg?seed=ecnerwala',0,'user-ecnerwala');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('emma_swift','https://api.dicebear.com/7.x/notionists/svg?seed=emma',0,'user-emma');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('jiangly','https://api.dicebear.com/7.x/notionists/svg?seed=jiangly',0,'user-jiangly');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('kevin_pro','https://api.dicebear.com/7.x/notionists/svg?seed=kevin',0,'user-kevin');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('lily_codes','https://api.dicebear.com/7.x/notionists/svg?seed=lily',0,'user-lily');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('max_coder','https://api.dicebear.com/7.x/notionists/svg?seed=max',0,'user-max');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('Petr','https://api.dicebear.com/7.x/notionists/svg?seed=petr',0,'user-petr');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('sara_dev','https://api.dicebear.com/7.x/notionists/svg?seed=sara',0,'user-sara');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('scott_wu','https://api.dicebear.com/7.x/notionists/svg?seed=scott',0,'user-scott');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('tom_quick','https://api.dicebear.com/7.x/notionists/svg?seed=tom',0,'user-tom');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('tourist','https://api.dicebear.com/7.x/notionists/svg?seed=tourist',0,'user-tourist');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('Um_nik','https://api.dicebear.com/7.x/notionists/svg?seed=um_nik',0,'user-um_nik');
INSERT INTO `forum_users` (`username`, `avatar`, `karma`, `id`) VALUES ('yuki_codes','https://api.dicebear.com/7.x/notionists/svg?seed=yuki',0,'user-yuki');

-- Table: forum_community_links (3 rows)
INSERT INTO `forum_community_links` (`id`, `community_id`, `label`, `url`, `description`, `sort_order`) VALUES ('link-interview-1','community-interview','面试备考指南','https://example.com/interview-guide',NULL,1);
INSERT INTO `forum_community_links` (`id`, `community_id`, `label`, `url`, `description`, `sort_order`) VALUES ('link-tech-1','community-technology','每周题解','https://example.com/editorial',NULL,1);
INSERT INTO `forum_community_links` (`id`, `community_id`, `label`, `url`, `description`, `sort_order`) VALUES ('link-tech-2','community-technology','Discord 服务器','https://discord.gg/ulticode',NULL,2);

-- Table: forum_community_rules (7 rows)
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-career-1','community-career','保持专业','所有职业讨论中保持专业和礼貌的交流。',1,NOW(3));
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-comp-1','community-compensation','诚实准确','分享准确的薪酬数据，帮助社区成员做出更好的决策。',1,NOW(3));
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-interview-1','community-interview','尊重他人','每个人的面试经历都不同。请保持支持和建设性的态度。',1,NOW(3));
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-interview-2','community-interview','保护机密','请勿分享受保密协议（NDA）约束的信息或面试题目。',2,NOW(3));
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-tech-1','community-technology','展示你的尝试','提出技术问题时，请附上代码片段或你的思路。',1,NOW(3));
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-tech-2','community-technology','建设性反馈','保持反馈可操作和尊重。',2,NOW(3));
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-tech-3','community-technology','使用剧透标签','对正在进行中的比赛，请用剧透标签标记题解内容。',3,NOW(3));
SET FOREIGN_KEY_CHECKS=1;
