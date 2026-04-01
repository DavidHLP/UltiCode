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
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-career','Career','career','Career advice, job opportunities, and professional development discussions.',8900,220,'Briefcase','#10B981',NULL,2100,28,189,1,1,2,'2024-01-01 00:00:00.000','PUBLIC');
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-compensation','Compensation','compensation','Discuss salaries, benefits, and compensation packages in tech.',15200,680,'DollarSign','#F59E0B',NULL,4850,92,541,1,1,3,'2024-01-01 00:00:00.000','PUBLIC');
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-interview','Interview Experience','interview','Share and discuss interview experiences, questions, and preparation strategies.',12500,450,'MessageSquare','#3B82F6',NULL,3420,45,312,1,1,1,'2024-01-01 00:00:00.000','PUBLIC');
INSERT INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`) VALUES ('community-technology','Technology','technology','Technical discussions, new technologies, algorithms, and best practices.',9800,305,'Cpu','#8B5CF6',NULL,2890,38,267,1,1,4,'2024-01-01 00:00:00.000','PUBLIC');

-- Table: forum_posts (3 rows)
INSERT INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`, `vote_state`, `is_saved`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('post-contest-tilt','community-technology','user-david',NULL,'The \"30-Minute Wall\": How do you reset mental state during a contest?','discussion',NULL,'[\"mindset\", \"psychology\", \"strategy\"]','Yesterday I bricked Q2. Spent 40 mins debugging a simple off-by-one error. After that, I couldn\'t focus on Q3/Q4 at all. My brain just felt \"foggy\" and panicked.\n\nDo you have any physical or mental protocols to hard-reset? I\'ve heard of people doing pushups or splashing water.',NULL,NULL,'neutral',0,5120,1,0,'2024-11-28 14:30:00.000','{\"saves\": 0, \"views\": 0, \"awards\": 0, \"shares\": 0, \"comments\": 7}',12,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`, `vote_state`, `is_saved`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('post-rust-hashmap','community-technology','u-002',NULL,'Why does Map feel slower than plain objects in JavaScript CP?','question',NULL,'[\"typescript\", \"performance\", \"hashing\"]','I\'ve been grinding AtCoder benchmarks and noticed a huge performance diff between standard Map and plain objects.',NULL,NULL,'neutral',0,4200,0,0,'2024-11-28 08:00:00.000','{\"saves\": 0, \"views\": 0, \"awards\": 0, \"shares\": 0, \"comments\": 6}',8,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`, `vote_state`, `is_saved`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('post-segtree-visual','community-technology','user-tourist',NULL,'Visual Guide to Segment Trees (Lazy Propagation)','showcase',NULL,'[\"tutorial\", \"segment-tree\", \"visualization\"]','I wrote a small interactive blog post visualizing how lazy tags flow down strictly during queries.\n\n[Link to visualization](https://example.com/segtree-vis)\n\nKey insight: \"Lazy tags are just pending operations\".\nMost bugs come from:\n1. Not pushing down before reading children.\n2. Not updating the current node after children return.\n\nLet me know if this helps!','[{\"src\": \"https://images.unsplash.com/photo-1509228468518-180dd4864904?auto=format&fit=crop&w=1200&q=80\", \"kind\": \"image\", \"type\": \"image\", \"ratio\": 1.777777777777778}]',NULL,'neutral',1,8900,0,0,'2024-11-27 10:00:00.000','{\"saves\": 0, \"views\": 0, \"awards\": 0, \"shares\": 0, \"comments\": 5}',0,0,NULL,NULL,0,NULL,NULL);

-- Table: forum_comments (18 rows)
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-1','post-rust-hashmap',NULL,'user-benq','Maps have extra overhead for hashing + boxed keys. For CP, a null-prototype object or array often wins if your keys are small integers.',NULL,'2024-11-28 09:20:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-2','post-rust-hashmap','c-rust-1','u-002','Ah makes sense. I assumed Map would be fastest by default. I will try a null-prototype object.',NULL,'2024-11-28 09:35:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-3','post-rust-hashmap','c-rust-2','user-petr','Be careful with objects: stringifying keys or using mixed types can tank performance. Stick to consistent key types.',NULL,'2024-11-28 10:00:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-4','post-rust-hashmap','c-rust-3','user-yuki','Do JS judges ever include adversarial key patterns? Or are test cases mostly static?',NULL,'2024-11-28 10:15:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-5','post-rust-hashmap','c-rust-4','user-petr','They are static, but bad key distributions still hurt. If key space is dense, use arrays.',NULL,'2024-11-28 10:30:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-rust-6','post-rust-hashmap',NULL,'user-alex','In JS, arrays are usually fastest for dense integer keys.',NULL,'2024-11-28 10:45:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-1','post-segtree-visual',NULL,'user-jiangly','Great visual. Small typo on slide 3: \"propogate\" -> \"propagate\".',NULL,'2024-11-27 10:10:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-2','post-segtree-visual','c-seg-1','user-tourist','Fixed! Thanks. 🙏',NULL,'2024-11-27 10:15:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-3','post-segtree-visual',NULL,'user-kevin','Does this handle beatbeats? (Segment tree beats)',NULL,'2024-11-27 10:30:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-4','post-segtree-visual','c-seg-3','user-tourist','Not yet. Beats requires tracking min/max/second_max which is harder to visualize cleanly.',NULL,'2024-11-27 10:45:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-seg-5','post-segtree-visual','c-seg-3','user-max','Check out JiDriver\'s blog for beats visuals.',NULL,'2024-11-27 11:00:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-1','post-contest-tilt',NULL,'u-001','Breathing protocol: 4 sec in, 4 hold, 4 out. Do it 3 times. It forces heart rate down mechanically.',NULL,'2024-11-28 14:40:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-2','post-contest-tilt','c-tilt-1','user-david','Will try this next mock. I usually just stare at the screen hyperventilating lol.',NULL,'2024-11-28 14:45:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-3','post-contest-tilt','c-tilt-2','user-lily','Also, stand up. Physically changing your posture resets the \"tunnel vision\".',NULL,'2024-11-28 14:50:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-4','post-contest-tilt',NULL,'user-scott','I usually rage quit and go play League. (Don\'t do this)',NULL,'2024-11-28 15:00:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-5','post-contest-tilt','c-tilt-4','user-tom','Lol literally me last Codeforces round.',NULL,'2024-11-28 15:10:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-6','post-contest-tilt',NULL,'user-sara','I drink cold water. The temperature shock wakes up the prefrontal cortex.',NULL,'2024-11-28 15:30:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);
INSERT INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `edited_at`, `is_pinned`, `is_locked`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c-tilt-7','post-contest-tilt','c-tilt-6','user-emma','Science!',NULL,'2024-11-28 15:45:00.000',NULL,0,0,0,NULL,NULL,0,NULL,NULL);

-- Table: forum_tags (9 rows)
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-data-structures','data-structures','data-structures',NULL,'#8B5CF6',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-hashing','hashing','hashing',NULL,'#3B82F6',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-mindset','mindset','mindset',NULL,'#8B5CF6',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-performance','performance','performance',NULL,'#F59E0B',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-psychology','psychology','psychology',NULL,'#EC4899',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-strategy','strategy','strategy',NULL,'#10B981',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-tutorial','tutorial','tutorial',NULL,'#06B6D4',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-typescript','typescript','typescript',NULL,'#3178C6',0,'2026-03-22 05:44:30.161');
INSERT INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`) VALUES ('tag-visualization','visualization','visualization',NULL,'#F59E0B',0,'2026-03-22 05:44:30.161');

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
INSERT INTO `forum_community_links` (`id`, `community_id`, `label`, `url`, `description`, `sort_order`) VALUES ('link-interview-1','community-interview','Interview Prep Guide','https://example.com/interview-guide',NULL,1);
INSERT INTO `forum_community_links` (`id`, `community_id`, `label`, `url`, `description`, `sort_order`) VALUES ('link-tech-1','community-technology','Weekly Editorial','https://example.com/editorial',NULL,1);
INSERT INTO `forum_community_links` (`id`, `community_id`, `label`, `url`, `description`, `sort_order`) VALUES ('link-tech-2','community-technology','Discord Server','https://discord.gg/ulticode',NULL,2);

-- Table: forum_community_rules (7 rows)
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-career-1','community-career','Stay professional','Maintain professional discourse in all career discussions.',1,'2024-01-01 00:00:00.000');
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-comp-1','community-compensation','Be honest and accurate','Share accurate compensation data to help the community.',1,'2024-01-01 00:00:00.000');
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-interview-1','community-interview','Be respectful','Everyone\'s interview experience is different. Be supportive and constructive.',1,'2024-01-01 00:00:00.000');
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-interview-2','community-interview','Protect confidentiality','Do not share confidential information or questions under NDA.',2,'2024-01-01 00:00:00.000');
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-tech-1','community-technology','Show your attempt','Include code snippets or reasoning with every technical question.',1,'2024-01-01 00:00:00.000');
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-tech-2','community-technology','Be constructive','Keep feedback actionable and respectful.',2,'2024-01-01 00:00:00.000');
INSERT INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`) VALUES ('rule-tech-3','community-technology','Use spoiler tags','Mark solutions with spoiler tags for ongoing contests.',3,'2024-01-01 00:00:00.000');
SET FOREIGN_KEY_CHECKS=1;
