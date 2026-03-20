-- CreateTable
CREATE TABLE `users` (
    `id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(120) NOT NULL,
    `name` VARCHAR(120) NULL,
    `email` VARCHAR(255) NULL,
    `avatar` VARCHAR(255) NULL,
    `password` VARCHAR(255) NULL,
    `bio` TEXT NULL,
    `company` VARCHAR(255) NULL,
    `github` VARCHAR(255) NULL,
    `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `location` VARCHAR(255) NULL,
    `twitter` VARCHAR(255) NULL,
    `website` VARCHAR(255) NULL,
    `preferred_language` VARCHAR(50) NULL,
    `role` ENUM('USER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN') NOT NULL DEFAULT 'USER',
    `is_active` BOOLEAN NOT NULL DEFAULT true,
    `is_banned` BOOLEAN NOT NULL DEFAULT false,
    `banned_until` DATETIME(3) NULL,
    `banned_reason` TEXT NULL,
    `last_login_at` DATETIME(3) NULL,
    `created_by` VARCHAR(40) NULL,
    `updated_by` VARCHAR(40) NULL,

    UNIQUE INDEX `users_username_key`(`username`),
    INDEX `users_role_idx`(`role`),
    INDEX `users_is_active_is_banned_idx`(`is_active`, `is_banned`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problems` (
    `id` BIGINT NOT NULL,
    `slug` VARCHAR(120) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `difficulty` ENUM('Easy', 'Medium', 'Hard') NOT NULL,
    `acceptance_rate` DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    `status` ENUM('solved', 'attempted', 'todo') NOT NULL DEFAULT 'todo',
    `is_premium` BOOLEAN NOT NULL DEFAULT false,
    `has_solution` BOOLEAN NOT NULL DEFAULT false,
    `completed_time` DATE NULL,
    `is_published` BOOLEAN NOT NULL DEFAULT true,
    `published_at` DATETIME(3) NULL,
    `published_by` VARCHAR(40) NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(40) NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_details` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `slug` VARCHAR(120) NOT NULL,
    `summary` TEXT NOT NULL,
    `companies` JSON NULL,
    `likes` INTEGER NOT NULL DEFAULT 0,
    `dislikes` INTEGER NOT NULL DEFAULT 0,
    `difficulty_rating` DECIMAL(5, 1) NOT NULL DEFAULT 1500.0,
    `updated_at` DATETIME(3) NOT NULL,
    `follow_up` TEXT NULL,
    `constraints_json` JSON NOT NULL,
    `hints` JSON NULL,

    UNIQUE INDEX `problem_details_problem_id_key`(`problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_tags` (
    `id` VARCHAR(40) NOT NULL,
    `label` VARCHAR(120) NOT NULL,
    `slug` VARCHAR(120) NULL,
    `color` VARCHAR(20) NULL,
    `description` TEXT NULL,
    `usage_count` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `problem_tags_slug_key`(`slug`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_tag_relations` (
    `problem_id` BIGINT NOT NULL,
    `tag_id` VARCHAR(40) NOT NULL,

    INDEX `problem_tag_relations_tag_id_fkey`(`tag_id`),
    PRIMARY KEY (`problem_id`, `tag_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_languages` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `label` VARCHAR(50) NOT NULL,
    `value` VARCHAR(50) NOT NULL,
    `style` VARCHAR(20) NULL,
    `starter_code` TEXT NOT NULL,

    INDEX `problem_languages_problem_id_fkey`(`problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_examples` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `example_order` INTEGER NOT NULL DEFAULT 0,
    `input_text` TEXT NOT NULL,
    `output_text` TEXT NOT NULL,
    `explanation` TEXT NULL,
    `inputs` JSON NULL,

    INDEX `problem_examples_problem_id_fkey`(`problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contests` (
    `id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(120) NOT NULL,
    `slug` VARCHAR(120) NOT NULL,
    `contest_type` ENUM('weekly', 'biweekly', 'special') NOT NULL,
    `start_time` DATETIME(3) NOT NULL,
    `duration_minutes` INTEGER NOT NULL,
    `status` ENUM('upcoming', 'running', 'finished') NOT NULL,
    `penalty_per_wrong` INTEGER NOT NULL DEFAULT 300,
    `scoring_mode` ENUM('SCORE', 'ICPC') NOT NULL DEFAULT 'SCORE',
    `tie_breaker` ENUM('LAST_SOLVE_TIME', 'TOTAL_ATTEMPTS', 'NONE') NOT NULL DEFAULT 'LAST_SOLVE_TIME',
    `registered_count` INTEGER NOT NULL DEFAULT 0,
    `participant_count` INTEGER NOT NULL DEFAULT 0,
    `is_rated` BOOLEAN NOT NULL DEFAULT true,
    `description` TEXT NULL,
    `cover_image` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(40) NULL,
    `is_visible` BOOLEAN NOT NULL DEFAULT true,
    `rules` TEXT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(40) NULL,

    INDEX `contests_status_start_time_idx`(`status`, `start_time`),
    INDEX `contests_contest_type_idx`(`contest_type`),
    INDEX `contests_slug_idx`(`slug`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_problems` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `problem_index` VARCHAR(10) NOT NULL,
    `score` INTEGER NOT NULL DEFAULT 0,
    `penalty_per_wrong` INTEGER NULL,
    `solved_count` INTEGER NOT NULL DEFAULT 0,
    `submission_count` INTEGER NOT NULL DEFAULT 0,

    INDEX `contest_problems_contest_id_idx`(`contest_id`),
    INDEX `contest_problems_problem_id_fkey`(`problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `global_rankings` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(120) NOT NULL,
    `global_rank` INTEGER NOT NULL,
    `rating` INTEGER NOT NULL DEFAULT 1500,
    `max_rating` INTEGER NOT NULL DEFAULT 1500,
    `contests_attended` INTEGER NOT NULL DEFAULT 0,
    `avatar` VARCHAR(255) NULL,
    `country` VARCHAR(10) NULL,
    `badge` VARCHAR(50) NULL,
    `contests_rated` INTEGER NOT NULL DEFAULT 0,
    `last_contest_id` VARCHAR(40) NULL,
    `max_rating_title` ENUM('NEWBIE', 'PUPIL', 'SPECIALIST', 'EXPERT', 'CANDIDATE_MASTER', 'MASTER', 'INTERNATIONAL_MASTER', 'GRANDMASTER', 'INTERNATIONAL_GRANDMASTER', 'LEGENDARY_GRANDMASTER') NOT NULL DEFAULT 'NEWBIE',
    `rating_title` ENUM('NEWBIE', 'PUPIL', 'SPECIALIST', 'EXPERT', 'CANDIDATE_MASTER', 'MASTER', 'INTERNATIONAL_MASTER', 'GRANDMASTER', 'INTERNATIONAL_GRANDMASTER', 'LEGENDARY_GRANDMASTER') NOT NULL DEFAULT 'NEWBIE',
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `global_rankings_user_id_key`(`user_id`),
    INDEX `global_rankings_global_rank_idx`(`global_rank`),
    INDEX `global_rankings_rating_idx`(`rating`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_rankings` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `rank` INTEGER NOT NULL,
    `rating_before` INTEGER NOT NULL DEFAULT 1500,
    `rating_after` INTEGER NOT NULL DEFAULT 1500,
    `rating_change` INTEGER NOT NULL DEFAULT 0,
    `is_virtual` BOOLEAN NOT NULL DEFAULT false,
    `solved_count` INTEGER NOT NULL DEFAULT 0,
    `total_penalty` INTEGER NOT NULL DEFAULT 0,
    `total_score` INTEGER NOT NULL DEFAULT 0,
    `finish_time` INTEGER NULL,
    `total_attempts` INTEGER NOT NULL DEFAULT 0,
    `problem_stats_snapshot` JSON NULL,

    INDEX `contest_rankings_contest_id_rank_idx`(`contest_id`, `rank`),
    INDEX `contest_rankings_user_id_idx`(`user_id`),
    UNIQUE INDEX `contest_rankings_contest_id_user_id_is_virtual_key`(`contest_id`, `user_id`, `is_virtual`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_participants` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `status` ENUM('REGISTERED', 'STARTED', 'FINISHED', 'DISQUALIFIED') NOT NULL DEFAULT 'REGISTERED',
    `registered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `started_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `is_virtual` BOOLEAN NOT NULL DEFAULT false,
    `final_rank` INTEGER NULL,
    `total_penalty` INTEGER NOT NULL DEFAULT 0,
    `total_score` INTEGER NOT NULL DEFAULT 0,
    `total_attempts` INTEGER NOT NULL DEFAULT 0,
    `last_solve_time` INTEGER NULL,
    `virtual_session_id` VARCHAR(40) NULL,

    INDEX `contest_participants_user_id_idx`(`user_id`),
    INDEX `contest_participants_contest_id_final_rank_idx`(`contest_id`, `final_rank`),
    INDEX `contest_participants_virtual_session_id_fkey`(`virtual_session_id`),
    UNIQUE INDEX `contest_participants_contest_id_user_id_virtual_session_id_key`(`contest_id`, `user_id`, `virtual_session_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_problem_results` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `contest_problem_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `participant_id` VARCHAR(40) NOT NULL,
    `ranking_id` VARCHAR(40) NULL,
    `is_solved` BOOLEAN NOT NULL DEFAULT false,
    `score` INTEGER NOT NULL DEFAULT 0,
    `attempts` INTEGER NOT NULL DEFAULT 0,
    `first_solve_time` INTEGER NULL,
    `penalty_time` INTEGER NOT NULL DEFAULT 0,
    `best_submission_id` VARCHAR(40) NULL,

    INDEX `contest_problem_results_contest_id_user_id_idx`(`contest_id`, `user_id`),
    INDEX `contest_problem_results_contest_problem_id_idx`(`contest_problem_id`),
    INDEX `contest_problem_results_ranking_id_fkey`(`ranking_id`),
    INDEX `contest_problem_results_user_id_fkey`(`user_id`),
    UNIQUE INDEX `contest_problem_results_participant_id_contest_problem_id_key`(`participant_id`, `contest_problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `virtual_contest_sessions` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `status` ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
    `started_at` DATETIME(3) NULL,
    `ends_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `total_score` INTEGER NOT NULL DEFAULT 0,
    `total_penalty` INTEGER NOT NULL DEFAULT 0,

    INDEX `virtual_contest_sessions_contest_id_user_id_idx`(`contest_id`, `user_id`),
    INDEX `virtual_contest_sessions_user_id_status_idx`(`user_id`, `status`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_submissions` (
    `id` VARCHAR(40) NOT NULL,
    `submission_id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `contest_problem_id` VARCHAR(40) NOT NULL,
    `participant_id` VARCHAR(40) NOT NULL,
    `virtual_session_id` VARCHAR(40) NULL,
    `submitted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `time_from_start` INTEGER NOT NULL,
    `is_accepted` BOOLEAN NOT NULL DEFAULT false,

    INDEX `contest_submissions_contest_id_participant_id_idx`(`contest_id`, `participant_id`),
    INDEX `contest_submissions_contest_problem_id_idx`(`contest_problem_id`),
    INDEX `contest_submissions_participant_id_fkey`(`participant_id`),
    INDEX `contest_submissions_submission_id_fkey`(`submission_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `notification_preferences` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `communication` BOOLEAN NOT NULL DEFAULT true,
    `marketing` BOOLEAN NOT NULL DEFAULT false,
    `security` BOOLEAN NOT NULL DEFAULT true,
    `system` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `notification_preferences_user_id_key`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `notifications` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `type` ENUM('COMMENT', 'REPLY', 'MENTION', 'UPVOTE', 'FOLLOW', 'SYSTEM', 'SUBMISSION', 'CONTEST') NOT NULL,
    `category` ENUM('COMMUNICATION', 'MARKETING', 'SECURITY', 'SYSTEM') NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `body` TEXT NOT NULL,
    `link` VARCHAR(255) NULL,
    `metadata` JSON NULL,
    `is_read` BOOLEAN NOT NULL DEFAULT false,
    `read_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `notifications_user_id_is_read_created_at_idx`(`user_id`, `is_read`, `created_at`),
    INDEX `notifications_user_id_type_idx`(`user_id`, `type`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_communities` (
    `id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `slug` VARCHAR(60) NOT NULL,
    `description` TEXT NOT NULL,
    `members` INTEGER NOT NULL DEFAULT 0,
    `online` INTEGER NOT NULL DEFAULT 0,
    `icon` VARCHAR(255) NULL,
    `color` VARCHAR(20) NULL,
    `banner` VARCHAR(255) NULL,
    `posts_count` INTEGER NOT NULL DEFAULT 0,
    `posts_today` INTEGER NOT NULL DEFAULT 0,
    `posts_week` INTEGER NOT NULL DEFAULT 0,
    `is_official` BOOLEAN NOT NULL DEFAULT false,
    `is_featured` BOOLEAN NOT NULL DEFAULT false,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `visibility` ENUM('PUBLIC', 'RESTRICTED', 'PRIVATE') NOT NULL DEFAULT 'PUBLIC',

    UNIQUE INDEX `forum_communities_slug_key`(`slug`),
    INDEX `forum_communities_slug_idx`(`slug`),
    INDEX `forum_communities_visibility_is_featured_idx`(`visibility`, `is_featured`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_users` (
    `username` VARCHAR(60) NOT NULL,
    `avatar` VARCHAR(255) NULL,
    `karma` INTEGER NOT NULL DEFAULT 0,
    `id` VARCHAR(40) NOT NULL,

    UNIQUE INDEX `forum_users_username_key`(`username`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_posts` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `permalink` VARCHAR(255) NULL,
    `title` VARCHAR(255) NOT NULL,
    `flair_type` ENUM('announcement', 'discussion', 'showcase', 'question', 'hiring') NULL,
    `flair_label` VARCHAR(60) NULL,
    `tags` JSON NOT NULL,
    `excerpt` TEXT NULL,
    `media` JSON NULL,
    `recommendation` JSON NULL,
    `vote_state` ENUM('upvoted', 'downvoted', 'neutral') NOT NULL DEFAULT 'neutral',
    `is_saved` BOOLEAN NOT NULL DEFAULT false,
    `impressions` INTEGER NOT NULL DEFAULT 0,
    `is_pinned` BOOLEAN NOT NULL DEFAULT false,
    `is_locked` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL,
    `stats` JSON NULL,
    `views` INTEGER NOT NULL DEFAULT 0,
    `is_flagged` BOOLEAN NOT NULL DEFAULT false,
    `flagged_reason` TEXT NULL,
    `flagged_at` DATETIME(3) NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(40) NULL,

    INDEX `forum_posts_community_id_fkey`(`community_id`),
    INDEX `forum_posts_user_id_fkey`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_comments` (
    `id` VARCHAR(40) NOT NULL,
    `post_id` VARCHAR(40) NOT NULL,
    `parent_id` VARCHAR(40) NULL,
    `author_id` VARCHAR(40) NOT NULL,
    `body` TEXT NOT NULL,
    `markdown` TEXT NULL,
    `created_at` DATETIME(3) NOT NULL,
    `edited_at` DATETIME(3) NULL,
    `is_pinned` BOOLEAN NOT NULL DEFAULT false,
    `is_locked` BOOLEAN NOT NULL DEFAULT false,
    `is_flagged` BOOLEAN NOT NULL DEFAULT false,
    `flagged_reason` TEXT NULL,
    `flagged_at` DATETIME(3) NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(40) NULL,

    INDEX `forum_comments_author_id_fkey`(`author_id`),
    INDEX `forum_comments_parent_id_fkey`(`parent_id`),
    INDEX `forum_comments_post_id_fkey`(`post_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
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

-- CreateTable
CREATE TABLE `forum_post_tag_relations` (
    `post_id` VARCHAR(40) NOT NULL,
    `tag_id` VARCHAR(40) NOT NULL,

    INDEX `forum_post_tag_relations_tag_id_idx`(`tag_id`),
    PRIMARY KEY (`post_id`, `tag_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_community_tags` (
    `community_id` VARCHAR(40) NOT NULL,
    `tag_id` VARCHAR(40) NOT NULL,
    `is_featured` BOOLEAN NOT NULL DEFAULT false,

    INDEX `forum_community_tags_tag_id_fkey`(`tag_id`),
    PRIMARY KEY (`community_id`, `tag_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
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

-- CreateTable
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

-- CreateTable
CREATE TABLE `forum_community_members` (
    `id` VARCHAR(40) NOT NULL,
    `community_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `role` ENUM('OWNER', 'MODERATOR', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `forum_community_members_user_id_idx`(`user_id`),
    UNIQUE INDEX `forum_community_members_community_id_user_id_key`(`community_id`, `user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
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

-- CreateTable
CREATE TABLE `problem_lists` (
    `id` VARCHAR(50) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `description` TEXT NULL,
    `author_id` VARCHAR(40) NOT NULL,
    `is_public` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    `is_featured` BOOLEAN NOT NULL DEFAULT false,
    `banner_tag` VARCHAR(30) NULL,
    `banner_icon` VARCHAR(50) NULL,
    `banner_theme` VARCHAR(30) NULL,
    `banner_order` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_list_problem_relations` (
    `list_id` VARCHAR(50) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `added_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `problem_list_problem_relations_problem_id_fkey`(`problem_id`),
    PRIMARY KEY (`list_id`, `problem_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `collections` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `description` TEXT NULL,
    `icon` VARCHAR(50) NULL,
    `color` VARCHAR(20) NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `is_default` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `collections_user_id_idx`(`user_id`),
    INDEX `collections_user_id_is_default_idx`(`user_id`, `is_default`),
    UNIQUE INDEX `collections_user_id_name_key`(`user_id`, `name`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `collection_items` (
    `id` VARCHAR(40) NOT NULL,
    `collection_id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(50) NOT NULL,
    `target_type` ENUM('PROBLEM', 'SOLUTION', 'FORUM_POST', 'PROBLEM_LIST', 'SOLUTION_COMMENT', 'FORUM_COMMENT') NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `note` TEXT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `collection_items_target_type_target_id_idx`(`target_type`, `target_id`),
    UNIQUE INDEX `collection_items_collection_id_target_type_target_id_key`(`collection_id`, `target_type`, `target_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `solutions` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `summary` TEXT NULL,
    `language` VARCHAR(50) NOT NULL,
    `tags` JSON NULL,
    `views` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `is_published` BOOLEAN NOT NULL DEFAULT true,
    `published_at` DATETIME(3) NULL,
    `published_by` VARCHAR(40) NULL,
    `is_flagged` BOOLEAN NOT NULL DEFAULT false,
    `flagged_reason` TEXT NULL,
    `flagged_at` DATETIME(3) NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(40) NULL,

    INDEX `solutions_problem_id_fkey`(`problem_id`),
    INDEX `solutions_user_id_fkey`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `solution_comments` (
    `id` VARCHAR(40) NOT NULL,
    `solution_id` VARCHAR(40) NOT NULL,
    `parent_id` VARCHAR(40) NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `is_flagged` BOOLEAN NOT NULL DEFAULT false,
    `flagged_reason` TEXT NULL,
    `flagged_at` DATETIME(3) NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(40) NULL,

    INDEX `solution_comments_parent_id_fkey`(`parent_id`),
    INDEX `solution_comments_solution_id_fkey`(`solution_id`),
    INDEX `solution_comments_user_id_fkey`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `edge_operations` (
    `id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('SOLUTION', 'SOLUTION_COMMENT', 'FORUM_POST', 'FORUM_COMMENT', 'PROBLEM', 'PROBLEM_LIST') NOT NULL,
    `operator_id` VARCHAR(40) NOT NULL,
    `operation_type` ENUM('VOTE_UP', 'VOTE_DOWN', 'ANALYZE') NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `edge_ops_target`(`target_type`, `target_id`),
    INDEX `edge_ops_operation_target`(`operation_type`, `target_type`, `target_id`),
    UNIQUE INDEX `edge_ops_unique`(`operator_id`, `operation_type`, `target_type`, `target_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_notes` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `content` TEXT NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `problem_notes_problem_id_fkey`(`problem_id`),
    UNIQUE INDEX `problem_notes_user_id_problem_id_key`(`user_id`, `problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `submissions` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `language` VARCHAR(50) NOT NULL,
    `code` TEXT NOT NULL,
    `status` VARCHAR(40) NOT NULL,
    `runtime` INTEGER NOT NULL,
    `memory` DOUBLE NOT NULL,
    `notes` TEXT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `runtime_percentile` DOUBLE NULL,
    `memory_percentile` DOUBLE NULL,
    `test_details` JSON NULL,
    `memoryDistBinsMb` JSON NULL,
    `runtimeDistBinsMs` JSON NULL,

    INDEX `submissions_problem_id_user_id_idx`(`problem_id`, `user_id`),
    INDEX `submissions_user_id_fkey`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `submission_statuses` (
    `key` VARCHAR(40) NOT NULL,
    `code` VARCHAR(10) NOT NULL,
    `label` VARCHAR(60) NOT NULL,
    `description` TEXT NULL,
    `suggestion` TEXT NULL,
    `category` VARCHAR(20) NOT NULL,
    `severity` VARCHAR(20) NOT NULL,
    `is_terminal` BOOLEAN NOT NULL DEFAULT true,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `submission_statuses_category_severity_idx`(`category`, `severity`),
    PRIMARY KEY (`key`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `views` (
    `id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('SOLUTION', 'FORUM_POST') NOT NULL,
    `user_id` VARCHAR(40) NULL,
    `ip` VARCHAR(45) NULL,
    `viewed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `views_target_type_target_id_user_id_ip_idx`(`target_type`, `target_id`, `user_id`, `ip`),
    INDEX `views_user_id_fkey`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `translations` (
    `id` VARCHAR(40) NOT NULL,
    `entity_type` VARCHAR(50) NOT NULL,
    `entity_id` VARCHAR(50) NOT NULL,
    `field_name` VARCHAR(50) NOT NULL,
    `locale` VARCHAR(10) NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `translations_entity_type_entity_id_locale_idx`(`entity_type`, `entity_id`, `locale`),
    INDEX `translations_locale_idx`(`locale`),
    UNIQUE INDEX `translations_entity_type_entity_id_field_name_locale_key`(`entity_type`, `entity_id`, `field_name`, `locale`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `password_resets` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `token` VARCHAR(255) NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `used_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `password_resets_token_key`(`token`),
    INDEX `password_resets_token_idx`(`token`),
    INDEX `password_resets_user_id_idx`(`user_id`),
    INDEX `password_resets_expires_at_idx`(`expires_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `user_permissions` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `action` ENUM('CREATE', 'READ', 'UPDATE', 'DELETE', 'MODERATE', 'PUBLISH', 'MANAGE_USERS', 'MANAGE_PERMISSIONS') NOT NULL,
    `resource` ENUM('USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST', 'FORUM_COMMENT', 'SYSTEM', 'PROBLEM_LIST', 'TAG') NOT NULL,
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
    `resource` ENUM('USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST', 'FORUM_COMMENT', 'SYSTEM', 'PROBLEM_LIST', 'TAG') NOT NULL,
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

-- CreateTable
CREATE TABLE `system_settings` (
    `key` VARCHAR(50) NOT NULL,
    `value` TEXT NOT NULL,
    `description` VARCHAR(255) NULL,
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`key`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `system_announcements` (
    `id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('COMMENT', 'REPLY', 'MENTION', 'UPVOTE', 'FOLLOW', 'SYSTEM', 'SUBMISSION', 'CONTEST') NOT NULL,
    `created_by` VARCHAR(40) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `system_announcement_reads` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `announcement_id` VARCHAR(40) NOT NULL,
    `is_read` BOOLEAN NOT NULL DEFAULT true,
    `read_at` DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `system_announcement_reads_user_id_announcement_id_key`(`user_id`, `announcement_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `problem_details` ADD CONSTRAINT `problem_details_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_tag_relations` ADD CONSTRAINT `problem_tag_relations_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_tag_relations` ADD CONSTRAINT `problem_tag_relations_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `problem_tags`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_languages` ADD CONSTRAINT `problem_languages_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_examples` ADD CONSTRAINT `problem_examples_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problems` ADD CONSTRAINT `contest_problems_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problems` ADD CONSTRAINT `contest_problems_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `global_rankings` ADD CONSTRAINT `global_rankings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_rankings` ADD CONSTRAINT `contest_rankings_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_rankings` ADD CONSTRAINT `contest_rankings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_participants` ADD CONSTRAINT `contest_participants_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_participants` ADD CONSTRAINT `contest_participants_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_participants` ADD CONSTRAINT `contest_participants_virtual_session_id_fkey` FOREIGN KEY (`virtual_session_id`) REFERENCES `virtual_contest_sessions`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_contest_problem_id_fkey` FOREIGN KEY (`contest_problem_id`) REFERENCES `contest_problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_participant_id_fkey` FOREIGN KEY (`participant_id`) REFERENCES `contest_participants`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_ranking_id_fkey` FOREIGN KEY (`ranking_id`) REFERENCES `contest_rankings`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `virtual_contest_sessions` ADD CONSTRAINT `virtual_contest_sessions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `virtual_contest_sessions` ADD CONSTRAINT `virtual_contest_sessions_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_contest_problem_id_fkey` FOREIGN KEY (`contest_problem_id`) REFERENCES `contest_problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_participant_id_fkey` FOREIGN KEY (`participant_id`) REFERENCES `contest_participants`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_submission_id_fkey` FOREIGN KEY (`submission_id`) REFERENCES `submissions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `notification_preferences` ADD CONSTRAINT `notification_preferences_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `notifications` ADD CONSTRAINT `notifications_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_posts` ADD CONSTRAINT `forum_posts_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_posts` ADD CONSTRAINT `forum_posts_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `forum_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_author_id_fkey` FOREIGN KEY (`author_id`) REFERENCES `forum_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_parent_id_fkey` FOREIGN KEY (`parent_id`) REFERENCES `forum_comments`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_post_tag_relations` ADD CONSTRAINT `forum_post_tag_relations_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_post_tag_relations` ADD CONSTRAINT `forum_post_tag_relations_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `forum_tags`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_community_tags` ADD CONSTRAINT `forum_community_tags_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_community_tags` ADD CONSTRAINT `forum_community_tags_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `forum_tags`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_community_rules` ADD CONSTRAINT `forum_community_rules_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_community_links` ADD CONSTRAINT `forum_community_links_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_community_members` ADD CONSTRAINT `forum_community_members_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_community_permissions` ADD CONSTRAINT `forum_community_permissions_community_id_fkey` FOREIGN KEY (`community_id`) REFERENCES `forum_communities`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_list_problem_relations` ADD CONSTRAINT `problem_list_problem_relations_list_id_fkey` FOREIGN KEY (`list_id`) REFERENCES `problem_lists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_list_problem_relations` ADD CONSTRAINT `problem_list_problem_relations_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `collections` ADD CONSTRAINT `collections_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `collection_items` ADD CONSTRAINT `collection_items_collection_id_fkey` FOREIGN KEY (`collection_id`) REFERENCES `collections`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solutions` ADD CONSTRAINT `solutions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solutions` ADD CONSTRAINT `solutions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comments` ADD CONSTRAINT `solution_comments_parent_id_fkey` FOREIGN KEY (`parent_id`) REFERENCES `solution_comments`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comments` ADD CONSTRAINT `solution_comments_solution_id_fkey` FOREIGN KEY (`solution_id`) REFERENCES `solutions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comments` ADD CONSTRAINT `solution_comments_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `edge_operations` ADD CONSTRAINT `edge_operations_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_notes` ADD CONSTRAINT `problem_notes_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_notes` ADD CONSTRAINT `problem_notes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `submissions` ADD CONSTRAINT `submissions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `submissions` ADD CONSTRAINT `submissions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `views` ADD CONSTRAINT `views_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `user_permissions` ADD CONSTRAINT `user_permissions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `audit_logs` ADD CONSTRAINT `audit_logs_performer_id_fkey` FOREIGN KEY (`performer_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `audit_logs` ADD CONSTRAINT `audit_logs_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `system_announcements` ADD CONSTRAINT `system_announcements_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `system_announcement_reads` ADD CONSTRAINT `system_announcement_reads_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `system_announcement_reads` ADD CONSTRAINT `system_announcement_reads_announcement_id_fkey` FOREIGN KEY (`announcement_id`) REFERENCES `system_announcements`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
