/*
 Navicat Premium Dump SQL

 Source Server         : WSL2-Mysql
 Source Server Type    : MySQL
 Source Server Version : 80406 (8.4.6)
 Source Host           : localhost:3306
 Source Schema         : spring_oj

 Target Server Type    : MySQL
 Target Server Version : 80406 (8.4.6)
 File Encoding         : 65001

 Date: 17/10/2025 18:18:54
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for auth_tokens
-- ----------------------------
DROP TABLE IF EXISTS `auth_tokens`;
CREATE TABLE `auth_tokens`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `user_id` bigint NOT NULL COMMENT '关联用户ID',
                                `token` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '令牌值',
                                `kind` enum('access','refresh','api') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '令牌类型',
                                `revoked` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已吊销',
                                `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `expires_at` timestamp NULL DEFAULT NULL COMMENT '过期时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE INDEX `uk_auth_tokens_token`(`token` ASC) USING BTREE,
                                INDEX `idx_auth_tokens_user`(`user_id` ASC, `kind` ASC, `revoked` ASC) USING BTREE,
                                CONSTRAINT `fk_auth_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 71 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of auth_tokens
-- ----------------------------
INSERT INTO `auth_tokens` VALUES (71, 1, '29efc2bd1c0a44f1965cc4950af33d85d7283d0f4d22de652df317137a4d4fb8', 'access', 0, '2025-10-20 10:00:00', '2025-10-27 10:00:00');
INSERT INTO `auth_tokens` VALUES (72, 1, '7ab1a6d4c1d78e5ff62ab3b7c4c685d7b19d9506b477a1f9c3b0c261d5bc4a21', 'refresh', 0, '2025-10-20 10:00:00', '2025-11-19 10:00:00');

-- ----------------------------
-- Table structure for security_audit_logs
-- ----------------------------
DROP TABLE IF EXISTS `security_audit_logs`;
CREATE TABLE `security_audit_logs`  (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审计日志ID',
                                        `actor_id` bigint NULL DEFAULT NULL COMMENT '操作者用户ID',
                                        `actor_username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作者用户名',
                                        `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型',
                                        `object_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '受影响对象类型',
                                        `object_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '受影响对象标识',
                                        `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述信息',
                                        `diff_snapshot` json NULL COMMENT '变更快照',
                                        `ip_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源IP',
                                        `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        PRIMARY KEY (`id`) USING BTREE,
                                        INDEX `idx_audit_actor`(`actor_id` ASC, `created_at` DESC) USING BTREE,
                                        INDEX `idx_audit_object`(`object_type` ASC, `object_id` ASC, `created_at` DESC) USING BTREE,
                                        CONSTRAINT `fk_audit_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of security_audit_logs
-- ----------------------------
INSERT INTO `security_audit_logs` (`id`, `actor_id`, `actor_username`, `action`, `object_type`, `object_id`, `description`, `diff_snapshot`, `ip_address`, `created_at`)
VALUES (1, 1, 'DavidHLP', 'ROLE_PERMISSION_UPDATED', 'role', '3', '调整管理员角色权限集合', JSON_OBJECT('before', JSON_ARRAY('problem.view', 'problem.manage'), 'after', JSON_ARRAY('problem.view', 'problem.manage', 'user.manage')), '127.0.0.1', '2025-10-20 10:05:00');

-- ----------------------------
-- Table structure for user_security_profiles
-- ----------------------------
DROP TABLE IF EXISTS `user_security_profiles`;
CREATE TABLE `user_security_profiles`  (
                                           `user_id` bigint NOT NULL COMMENT '用户ID',
                                           `mfa_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '二次验证密钥',
                                           `mfa_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启二次验证',
                                           `sso_binding` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SSO绑定标识',
                                           `last_mfa_verified_at` timestamp NULL DEFAULT NULL COMMENT '最近二次验证时间',
                                           `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           PRIMARY KEY (`user_id`) USING BTREE,
                                           CONSTRAINT `fk_security_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_security_profiles
-- ----------------------------
INSERT INTO `user_security_profiles` VALUES (1, NULL, 0, NULL, NULL, '2025-10-15 08:47:45', '2025-10-17 13:13:57');

-- ----------------------------
-- Table structure for sso_sessions
-- ----------------------------
DROP TABLE IF EXISTS `sso_sessions`;
CREATE TABLE `sso_sessions`  (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SSO会话ID',
                                 `user_id` bigint NOT NULL COMMENT '用户ID',
                                 `client_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接入方ID',
                                 `session_token` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话令牌',
                                 `state` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务状态/重定向态',
                                 `expires_at` timestamp NOT NULL COMMENT '过期时间',
                                 `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`id`) USING BTREE,
                                 UNIQUE INDEX `uk_sso_session_token`(`session_token` ASC) USING BTREE,
                                 INDEX `idx_sso_user_client`(`user_id` ASC, `client_id` ASC, `expires_at` DESC) USING BTREE,
                                 CONSTRAINT `fk_sso_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sso_sessions
-- ----------------------------
INSERT INTO `sso_sessions` (`id`, `user_id`, `client_id`, `session_token`, `state`, `expires_at`, `created_at`)
VALUES (1, 1, 'admin-console', '0bb2d4d5aa744913b4050a4f27191d58e92d8fd10f2cbb73c6bd348c21f1a908', 'dashboard', '2025-10-20 12:00:00', '2025-10-20 09:30:00');

-- ----------------------------
-- Table structure for ip_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `ip_blacklist`;
CREATE TABLE `ip_blacklist`  (
                                 `ip_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被封禁的IP',
                                 `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '封禁原因',
                                 `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `expires_at` timestamp NULL DEFAULT NULL COMMENT '到期时间',
                                 PRIMARY KEY (`ip_address`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ip_blacklist
-- ----------------------------
INSERT INTO `ip_blacklist` VALUES ('203.0.113.10', '疑似撞库', '2025-10-19 20:00:00', '2025-11-19 20:00:00');

-- ----------------------------
-- Table structure for bookmarks
-- ----------------------------
DROP TABLE IF EXISTS `bookmarks`;
CREATE TABLE `bookmarks` (
                              `user_id` bigint NOT NULL COMMENT '用户ID',
                              `entity_type` enum('problem','contest','discussion','comment') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体类型',
                              `entity_id` bigint NOT NULL COMMENT '实体主键ID',
                              `visibility` enum('private','public','team') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'private' COMMENT '可见范围',
                              `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
                              `tags` json NULL COMMENT '标签集合',
                              `source` enum('user','system','migration') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '来源',
                              `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
                              `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`user_id`, `entity_type`, `entity_id`) USING BTREE,
                              INDEX `idx_bookmarks_entity`(`entity_type` ASC, `entity_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bookmarks
-- ----------------------------
INSERT INTO `bookmarks` (`user_id`, `entity_type`, `entity_id`, `visibility`, `note`, `tags`, `source`, `created_at`, `updated_at`)
VALUES
    (1, 'problem', 1, 'private', '常用题目，需二刷', JSON_ARRAY('数组', '技巧'), 'user', '2025-10-20 09:12:00', '2025-10-20 09:12:00'),
    (1, 'contest', 1, 'public', NULL, NULL, 'system', '2025-10-20 09:12:30', '2025-10-20 09:12:30');

-- ----------------------------
-- Table structure for categories
-- ----------------------------
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories`  (
                               `id` smallint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
                               `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
                               `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
                               PRIMARY KEY (`id`) USING BTREE,
                               UNIQUE INDEX `uk_categories_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of categories
-- ----------------------------
INSERT INTO `categories` VALUES (1, 'algorithms', 'Algorithms');
INSERT INTO `categories` VALUES (2, 'data-structures', 'Data Structures');

-- ----------------------------
-- Table structure for comments
-- ----------------------------
DROP TABLE IF EXISTS `comments`;
CREATE TABLE `comments` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID',
                             `entity_type` enum('problem','contest','submission','comment','discussion') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体类型',
                             `entity_id` bigint NOT NULL COMMENT '实体ID',
                             `user_id` bigint NOT NULL COMMENT '评论用户ID',
                             `parent_id` bigint NULL DEFAULT NULL COMMENT '父评论ID',
                             `status` enum('pending','approved','rejected','hidden') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '审核状态',
                             `visibility` enum('public','private','internal') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'public' COMMENT '可见性',
                             `content_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容Markdown',
                             `content_rendered` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '渲染后的内容',
                             `sensitive_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否命中敏感词',
                             `sensitive_hits` json NULL COMMENT '敏感词命中详情',
                             `moderation_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '风险等级',
                             `moderation_notes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核备注',
                             `last_moderated_by` bigint NULL DEFAULT NULL COMMENT '最后审核人',
                             `last_moderated_at` timestamp NULL DEFAULT NULL COMMENT '最后审核时间',
                             `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`) USING BTREE,
                             INDEX `idx_comments_entity`(`entity_type` ASC, `entity_id` ASC, `created_at` ASC) USING BTREE,
                             INDEX `idx_comments_status`(`status` ASC, `created_at` ASC) USING BTREE,
                             INDEX `idx_comments_user`(`user_id` ASC, `created_at` ASC) USING BTREE,
                             INDEX `idx_comments_parent`(`parent_id` ASC) USING BTREE,
                             CONSTRAINT `fk_comments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                             CONSTRAINT `fk_comments_parent` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                             CONSTRAINT `fk_comments_moderator` FOREIGN KEY (`last_moderated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of comments
-- ----------------------------
INSERT INTO `comments` (`id`, `entity_type`, `entity_id`, `user_id`, `parent_id`, `status`, `visibility`, `content_md`, `content_rendered`, `sensitive_flag`, `sensitive_hits`, `moderation_level`, `moderation_notes`, `last_moderated_by`, `last_moderated_at`, `created_at`, `updated_at`)
VALUES
    (1, 'problem', 1, 1, NULL, 'approved', 'public', '这是一道很经典的题目，谢谢分享！', NULL, 0, NULL, 'low', '自动通过', 1, '2025-10-20 09:20:00', '2025-10-20 09:15:00', '2025-10-20 09:20:00'),
    (2, 'problem', 1, 1, 1, 'pending', 'public', '期待继续优化题解思路', NULL, 0, NULL, NULL, NULL, NULL, NULL, '2025-10-20 09:18:00', '2025-10-20 09:18:00');

-- ----------------------------
-- Table structure for contest_participants
-- ----------------------------
DROP TABLE IF EXISTS `contest_participants`;
CREATE TABLE `contest_participants`  (
                                         `contest_id` bigint NOT NULL COMMENT '比赛ID',
                                         `user_id` bigint NOT NULL COMMENT '参赛用户ID',
                                         `registered_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
                                         PRIMARY KEY (`contest_id`, `user_id`) USING BTREE,
                                         INDEX `idx_cpart_user`(`user_id` ASC, `contest_id` ASC) USING BTREE,
                                         CONSTRAINT `fk_cpart_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                         CONSTRAINT `fk_cpart_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for contest_registrations
-- ----------------------------
DROP TABLE IF EXISTS `contest_registrations`;
CREATE TABLE `contest_registrations`  (
                                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '报名记录ID',
                                          `contest_id` bigint NOT NULL COMMENT '比赛ID',
                                          `user_id` bigint NOT NULL COMMENT '用户ID',
                                          `status` enum('pending','approved','rejected','cancelled') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '报名状态',
                                          `source` enum('self','invite','admin') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'self' COMMENT '报名来源',
                                          `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
                                          `reviewed_by` bigint NULL DEFAULT NULL COMMENT '审核人',
                                          `reviewed_at` timestamp NULL DEFAULT NULL COMMENT '审核时间',
                                          `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                          PRIMARY KEY (`id`) USING BTREE,
                                          UNIQUE INDEX `uk_contest_registration_user`(`contest_id` ASC, `user_id` ASC) USING BTREE,
                                          INDEX `idx_creg_status`(`contest_id` ASC, `status` ASC, `created_at` ASC) USING BTREE,
                                          CONSTRAINT `fk_creg_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                          CONSTRAINT `fk_creg_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                          CONSTRAINT `fk_creg_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of contest_participants
-- ----------------------------
INSERT INTO `contest_participants` VALUES (1, 1, '2025-10-20 09:05:00');

-- ----------------------------
-- Records of contest_registrations
-- ----------------------------
INSERT INTO `contest_registrations` VALUES (1, 1, 1, 'approved', 'admin', '系统导入', 1, '2025-10-20 09:05:00', '2025-10-20 09:00:00', '2025-10-20 09:05:00');

-- ----------------------------
-- Table structure for contest_problems
-- ----------------------------
DROP TABLE IF EXISTS `contest_problems`;
CREATE TABLE `contest_problems`  (
                                     `contest_id` bigint NOT NULL COMMENT '比赛ID',
                                     `problem_id` bigint NOT NULL COMMENT '题目ID',
                                     `alias` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '题目别名',
                                     `points` int NULL DEFAULT NULL COMMENT '题目分值',
                                     `order_no` int NULL DEFAULT 0 COMMENT '题目排序号',
                                     PRIMARY KEY (`contest_id`, `problem_id`) USING BTREE,
                                     INDEX `idx_cp_order`(`contest_id` ASC, `order_no` ASC) USING BTREE,
                                     INDEX `fk_cp_problem`(`problem_id` ASC) USING BTREE,
                                     CONSTRAINT `fk_cp_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                     CONSTRAINT `fk_cp_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of contest_problems
-- ----------------------------
INSERT INTO `contest_problems` VALUES (1, 1, 'A', 100, 1);
INSERT INTO `contest_problems` VALUES (1, 2, 'B', 100, 2);

-- ----------------------------
-- Table structure for contests
-- ----------------------------
DROP TABLE IF EXISTS `contests`;
CREATE TABLE `contests`  (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '比赛ID',
                             `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '比赛标题',
                             `description_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '比赛描述Markdown',
                             `kind` enum('icpc','oi','ioi','cf','acm','custom') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'icpc' COMMENT '比赛类型',
                             `start_time` datetime NOT NULL COMMENT '开始时间',
                             `end_time` datetime NOT NULL COMMENT '结束时间',
                             `is_visible` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否公开',
                             `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                             `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `registration_mode` enum('open','approval','invite_only') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'open' COMMENT '报名模式',
                             `registration_start_time` datetime NULL DEFAULT NULL COMMENT '报名开始时间',
                             `registration_end_time` datetime NULL DEFAULT NULL COMMENT '报名结束时间',
                             `max_participants` int NULL DEFAULT NULL COMMENT '参赛人数上限',
                             `penalty_per_wrong` int NOT NULL DEFAULT 20 COMMENT '每次罚时(分钟)',
                             `scoreboard_freeze_minutes` int NOT NULL DEFAULT 0 COMMENT '封榜提前分钟数',
                             `hide_score_during_freeze` tinyint(1) NOT NULL DEFAULT 1 COMMENT '封榜期隐藏最新成绩',
                             PRIMARY KEY (`id`) USING BTREE,
                             INDEX `idx_contest_time`(`start_time` ASC, `end_time` ASC) USING BTREE,
                             INDEX `idx_contest_registration_window`(`registration_start_time` ASC, `registration_end_time` ASC) USING BTREE,
                             INDEX `fk_contest_creator`(`created_by` ASC) USING BTREE,
                             CONSTRAINT `fk_contest_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of contests
-- ----------------------------
INSERT INTO `contests` VALUES (1, '秋季热身赛', '一次针对初学者的热身赛，包含两道基础题。', 'icpc', '2025-11-01 09:00:00', '2025-11-01 12:00:00', 1, 1, '2025-10-20 09:00:00', '2025-10-20 09:00:00', 'open', '2025-10-15 00:00:00', '2025-10-31 23:59:59', NULL, 20, 30, 1);

-- ----------------------------
-- Table structure for datasets
-- ----------------------------
DROP TABLE IF EXISTS `datasets`;
CREATE TABLE `datasets`  (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
                             `problem_id` bigint NOT NULL COMMENT '关联题目ID',
                             `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数据集名称',
                             `is_active` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为激活数据集',
                             `checker_type` enum('text','float','custom') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'text' COMMENT '校验器类型',
                             `checker_file_id` bigint NULL DEFAULT NULL COMMENT '自定义校验器文件ID',
                             `float_abs_tol` double NULL DEFAULT NULL COMMENT '浮点绝对误差阈值',
                             `float_rel_tol` double NULL DEFAULT NULL COMMENT '浮点相对误差阈值',
                             `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                             `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`) USING BTREE,
                             INDEX `idx_ds_problem`(`problem_id` ASC, `is_active` ASC) USING BTREE,
                             INDEX `fk_ds_checker_file`(`checker_file_id` ASC) USING BTREE,
                             INDEX `fk_ds_creator`(`created_by` ASC) USING BTREE,
                             CONSTRAINT `fk_ds_checker_file` FOREIGN KEY (`checker_file_id`) REFERENCES `files` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
                             CONSTRAINT `fk_ds_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
                             CONSTRAINT `fk_ds_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of datasets
-- ----------------------------
INSERT INTO `datasets` VALUES (1, 1, 'v1', 1, 'text', NULL, NULL, NULL, 1, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `datasets` VALUES (2, 2, 'v1', 1, 'text', NULL, NULL, NULL, 1, '2025-10-17 16:53:25', '2025-10-17 16:53:25');

-- ----------------------------
-- Table structure for difficulties
-- ----------------------------
DROP TABLE IF EXISTS `difficulties`;
CREATE TABLE `difficulties`  (
                                 `id` tinyint NOT NULL COMMENT '难度ID',
                                 `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '难度编码',
                                 `sort_key` tinyint NOT NULL COMMENT '排序键',
                                 PRIMARY KEY (`id`) USING BTREE,
                                 UNIQUE INDEX `uk_difficulties_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of difficulties
-- ----------------------------
INSERT INTO `difficulties` VALUES (1, 'easy', 1);
INSERT INTO `difficulties` VALUES (2, 'medium', 2);
INSERT INTO `difficulties` VALUES (3, 'hard', 3);

-- ----------------------------
-- Table structure for files
-- ----------------------------
DROP TABLE IF EXISTS `files`;
CREATE TABLE `files`  (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
                          `storage_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径Key',
                          `sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件哈希',
                          `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MIME类型',
                          `size_bytes` bigint NULL DEFAULT NULL COMMENT '文件大小字节数',
                          `created_by` bigint NULL DEFAULT NULL COMMENT '上传用户ID',
                          `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                          PRIMARY KEY (`id`) USING BTREE,
                          UNIQUE INDEX `uk_files_sha`(`sha256` ASC) USING BTREE,
                          INDEX `idx_files_creator`(`created_by` ASC) USING BTREE,
                          CONSTRAINT `fk_files_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of files
-- ----------------------------
INSERT INTO `files` VALUES (1, 'submissions/2025/10/17/1.cpp', '0f4b6cba72f787b8581fbc5eac109d3328d01fefbe17f4ad6d4b5d3c7f27e9a1', 'text/x-c++src', 428, 1, '2025-10-17 17:44:00');
INSERT INTO `files` VALUES (2, 'submissions/2025/10/17/1.log', '31ed948a9920ba63dd1d8c2f954ed7be16ceb2068aec1a51ad8f7c837f0e9b77', 'text/plain', 182, 1, '2025-10-17 17:45:05');
INSERT INTO `files` VALUES (3, 'submissions/2025/10/18/2.py', 'd755ca5ecd39919a4389d88bc54a8fdae8c09e24bb47e2d086e6c5b1e1f72cfa', 'text/x-python', 312, 1, '2025-10-18 09:10:00');
INSERT INTO `files` VALUES (4, 'submissions/2025/10/18/2.log', '18b3543ebb7f8c580c54529974a51835e1f3ff5ffc87dc5d5059070f0d1226c3', 'text/plain', 512, 1, '2025-10-18 09:12:15');
INSERT INTO `files` VALUES (5, 'submissions/2025/10/18/3.java', '0a3d93aee7d6daab6121fdf642e14e8f09db64a28fb7e5f0b8abb1fd997deff0', 'text/x-java-source', 640, 1, '2025-10-18 09:30:00');
INSERT INTO `files` VALUES (6, 'submissions/2025/10/18/3.log', '5a00be31054cf2bdbc83e92ed3524cd86f55f6aefe646fec4cb1b6f9fedfb511', 'text/plain', 420, 1, '2025-10-18 09:31:10');

-- ----------------------------
-- Table structure for judge_jobs
-- ----------------------------
DROP TABLE IF EXISTS `judge_jobs`;
CREATE TABLE `judge_jobs`  (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评测任务ID',
                               `submission_id` bigint NOT NULL COMMENT '关联提交ID',
                               `node_id` bigint NULL DEFAULT NULL COMMENT '执行节点ID',
                               `status` enum('queued','running','finished','failed','canceled') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'queued' COMMENT '任务状态',
                               `priority` tinyint NOT NULL DEFAULT 0 COMMENT '任务优先级',
                               `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
                               `finished_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
                               PRIMARY KEY (`id`) USING BTREE,
                               UNIQUE INDEX `uk_job_submission`(`submission_id` ASC) USING BTREE,
                               INDEX `idx_jobs_status`(`status` ASC, `priority` ASC, `created_at` ASC) USING BTREE,
                               INDEX `fk_jobs_node`(`node_id` ASC) USING BTREE,
                               CONSTRAINT `fk_jobs_node` FOREIGN KEY (`node_id`) REFERENCES `judge_nodes` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
                               CONSTRAINT `fk_jobs_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of judge_jobs
-- ----------------------------
INSERT INTO `judge_jobs` VALUES (1, 1, 1, 'finished', 0, '2025-10-17 17:46:00', '2025-10-17 17:46:05', '2025-10-17 17:46:07');
INSERT INTO `judge_jobs` VALUES (2, 2, 2, 'running', 1, '2025-10-18 09:10:05', '2025-10-18 09:10:10', NULL);
INSERT INTO `judge_jobs` VALUES (3, 3, 3, 'failed', 0, '2025-10-18 09:30:05', '2025-10-18 09:30:07', '2025-10-18 09:30:20');

-- ----------------------------
-- Table structure for judge_nodes
-- ----------------------------
DROP TABLE IF EXISTS `judge_nodes`;
CREATE TABLE `judge_nodes`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评测节点ID',
                                `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点名称',
                                `status` enum('online','offline','busy','draining') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'online' COMMENT '节点状态',
                                `runtime_info` json NULL COMMENT '节点运行信息',
                                `last_heartbeat` timestamp NULL DEFAULT NULL COMMENT '最后心跳',
                                `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE INDEX `uk_jnode_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of judge_nodes
-- ----------------------------
INSERT INTO `judge_nodes` VALUES (1, 'node-1', 'online', '{"cpu":"16c","mem_gb":32,"os":"Ubuntu 24.04"}', '2025-10-17 17:50:00', '2025-10-15 08:00:00');
INSERT INTO `judge_nodes` VALUES (2, 'node-2', 'busy', '{"cpu":"32c","mem_gb":64,"os":"Debian 12"}', '2025-10-17 17:55:00', '2025-10-15 08:10:00');
INSERT INTO `judge_nodes` VALUES (3, 'node-3', 'offline', '{"cpu":"8c","mem_gb":16,"os":"Ubuntu 22.04"}', '2025-10-17 16:10:00', '2025-10-16 09:00:00');

-- ----------------------------
-- Table structure for languages
-- ----------------------------
DROP TABLE IF EXISTS `languages`;
CREATE TABLE `languages`  (
                              `id` smallint NOT NULL AUTO_INCREMENT COMMENT '语言ID',
                              `code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言编码',
                              `display_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
                              `runtime_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运行镜像',
                              `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                              PRIMARY KEY (`id`) USING BTREE,
                              UNIQUE INDEX `uk_languages_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of languages
-- ----------------------------
INSERT INTO `languages` VALUES (1, 'cpp17', 'C++17', NULL, 1);
INSERT INTO `languages` VALUES (2, 'python3.11', 'Python 3.11', NULL, 1);
INSERT INTO `languages` VALUES (3, 'java17', 'Java 17', NULL, 1);

-- ----------------------------
-- Table structure for permissions
-- ----------------------------
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
                                `code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
                                `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
                                `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE INDEX `uk_permissions_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of permissions
-- ----------------------------
INSERT INTO `permissions` VALUES (1, 'problem.view', '查看题目', '2025-10-14 08:00:00');
INSERT INTO `permissions` VALUES (2, 'problem.manage', '管理题目', '2025-10-14 08:00:00');
INSERT INTO `permissions` VALUES (3, 'contest.view', '查看比赛', '2025-10-14 08:00:00');
INSERT INTO `permissions` VALUES (4, 'contest.manage', '管理比赛', '2025-10-14 08:00:00');
INSERT INTO `permissions` VALUES (5, 'user.manage', '管理用户', '2025-10-14 08:00:00');
INSERT INTO `permissions` VALUES (6, 'user.view', '查看用户', '2025-10-14 08:05:00');
INSERT INTO `permissions` VALUES (7, 'role.manage', '管理角色', '2025-10-14 08:05:00');
INSERT INTO `permissions` VALUES (8, 'permission.manage', '管理权限', '2025-10-14 08:05:00');
INSERT INTO `permissions` VALUES (9, 'token.manage', '管理认证令牌', '2025-10-14 08:05:00');
INSERT INTO `permissions` VALUES (10, 'audit.view', '查看审计日志', '2025-10-14 08:05:00');
INSERT INTO `permissions` VALUES (11, 'security.policy.manage', '管理安全策略', '2025-10-14 08:05:00');

-- ----------------------------
-- Table structure for problem_language_configs
-- ----------------------------
DROP TABLE IF EXISTS `problem_language_configs`;
CREATE TABLE `problem_language_configs`  (
                                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
                                             `problem_id` bigint NOT NULL COMMENT '题目ID',
                                             `language_id` smallint NOT NULL COMMENT '语言ID',
                                             `function_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '函数入口名',
                                             `starter_code` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '初始代码模板',
                                             PRIMARY KEY (`id`) USING BTREE,
                                             UNIQUE INDEX `uk_problem_language`(`problem_id` ASC, `language_id` ASC) USING BTREE,
                                             INDEX `idx_plc_problem`(`problem_id` ASC) USING BTREE,
                                             INDEX `fk_plc_language`(`language_id` ASC) USING BTREE,
                                             CONSTRAINT `fk_plc_language` FOREIGN KEY (`language_id`) REFERENCES `languages` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                             CONSTRAINT `fk_plc_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2113 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of problem_language_configs
-- ----------------------------
INSERT INTO `problem_language_configs` VALUES (2101, 2, 1, 'reverseString', '// LeetCode-style C++17 (method only, returns string)\r\n     // For in-place version: void reverseString(vector<char>& s)\r\n     #include <bits/stdc++.h>\r\n     using namespace std;\r\n     class Solution {\r\n     public:\r\n         string reverseString(string s) {\r\n             reverse(s.begin(), s.end());\r\n             return s;\r\n         }\r\n     };');
INSERT INTO `problem_language_configs` VALUES (2102, 2, 2, 'reverseString', '# LeetCode-style Python 3.11 (method only, returns string)\r\n     class Solution:\r\n         def reverseString(self, s: str) -> str:\r\n             return s[::-1]\r\n\r\n     # In-place version:\r\n     # from typing import List\r\n     # class Solution:\r\n     #     def reverseString(self, s: List[str]) -> None:\r\n     #         s.reverse()\r\n     ');
INSERT INTO `problem_language_configs` VALUES (2103, 2, 3, 'reverseString', '// LeetCode-style Java 17 (method only, returns string)\r\n     // In-place version: void reverseString(char[] s)\r\n     class Solution {\r\n         public String reverseString(String s) {\r\n             return new StringBuilder(s).reverse().toString();\r\n         }\r\n     }');
INSERT INTO `problem_language_configs` VALUES (2110, 1, 1, 'twoSum', '// LeetCode-style C++17 (method only)\r\n     #include <bits/stdc++.h>\r\n     using namespace std;\r\n     class Solution {\r\n     public:\r\n         vector<int> twoSum(vector<int>& nums, int target) {\r\n             unordered_map<int,int> mp;\r\n             for (int i = 0; i < (int)nums.size(); ++i) {\r\n                 int need = target - nums[i];\r\n                 if (mp.count(need)) return {mp[need], i};\r\n                 mp[nums[i]] = i;\r\n             }\r\n             return {};\r\n         }\r\n     };');
INSERT INTO `problem_language_configs` VALUES (2111, 1, 2, 'twoSum', '# LeetCode-style Python 3.11 (method only)\r\n     from typing import List\r\n\r\n     class Solution:\r\n         def twoSum(self, nums: List[int], target: int) -> List[int]:\r\n             mp = {}\r\n             for i, x in enumerate(nums):\r\n                 if target - x in mp:\r\n                     return [mp[target - x], i]\r\n                 mp[x] = i\r\n             return []\r\n     ');
INSERT INTO `problem_language_configs` VALUES (2112, 1, 3, 'twoSum', '// LeetCode-style Java 17 (method only)\r\n     import java.util.*;\r\n     class Solution {\r\n         public int[] twoSum(int[] nums, int target) {\r\n             Map<Integer, Integer> mp = new HashMap<>();\r\n             for (int i = 0; i < nums.length; i++) {\r\n                 int need = target - nums[i];\r\n                 if (mp.containsKey(need)) return new int[]{mp.get(need), i};\r\n                 mp.put(nums[i], i);\r\n             }\r\n             return new int[0];\r\n         }\r\n     }');

-- ----------------------------
-- Table structure for problem_statements
-- ----------------------------
DROP TABLE IF EXISTS `problem_statements`;
CREATE TABLE `problem_statements`  (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题面ID',
                                       `problem_id` bigint NOT NULL COMMENT '题目ID',
                                       `lang_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言编码',
                                       `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
                                       `description_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目描述Markdown',
                                       `constraints_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '约束信息Markdown',
                                       `examples_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '示例说明Markdown',
                                       `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       PRIMARY KEY (`id`) USING BTREE,
                                       UNIQUE INDEX `uk_problem_lang`(`problem_id` ASC, `lang_code` ASC) USING BTREE,
                                       CONSTRAINT `fk_ps_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 209 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of problem_statements
-- ----------------------------
INSERT INTO `problem_statements` VALUES (201, 2, 'en', '344. Reverse String', 'Write a function that returns the reversed string of **s**.\r\n\r\n  > Note: The original LeetCode version modifies `char[]/List[str]` in-place. Here we use a return-value variant for judging.', '- 1 ≤ |s| ≤ 10^5', '**Example**\nInput: s = \"hello\"\nOutput: \"olleh\"', '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `problem_statements` VALUES (202, 2, 'zh-CN', '344. 反转字符串', '编写函数返回字符串 **s** 的反转结果。\r\n  > 注：LeetCode 原题为原地修改字符数组，这里改为返回值版本以便判题。', '- 1 ≤ |s| ≤ 10^5', '**示例**\n输入：s = \"hello\"\n输出：\"olleh\"', '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `problem_statements` VALUES (207, 1, 'en', '1. Two Sum', 'Given an integer array **nums** and an integer **target**, return the indices of the two numbers such that they add up to **target**.', '- 2 ≤ |nums| ≤ 10^5\n- -10^9 ≤ nums[i], target ≤ 10^9\n- Exactly one solution exists.', '**Example**\nInput: nums = [2,7,11,15], target = 9\nOutput: [0,1]', '2025-10-17 18:06:54', '2025-10-17 18:06:54');
INSERT INTO `problem_statements` VALUES (208, 1, 'zh-CN', '1. 两数之和', '给定整数数组 **nums** 与整数 **target**，请返回使得 `nums[i] + nums[j] = target` 的两个下标（i ≠ j）。', '- 2 ≤ |nums| ≤ 10^5\n- -10^9 ≤ nums[i], target ≤ 10^9\n- 题目保证恰好存在一个解。', '**示例**\n输入：nums = [2,7,11,15], target = 9\n输出：[0,1]', '2025-10-17 18:06:54', '2025-10-17 18:06:54');

-- ----------------------------
-- Table structure for problem_tags
-- ----------------------------
DROP TABLE IF EXISTS `problem_tags`;
CREATE TABLE `problem_tags`  (
                                 `problem_id` bigint NOT NULL COMMENT '题目ID',
                                 `tag_id` bigint NOT NULL COMMENT '标签ID',
                                 PRIMARY KEY (`problem_id`, `tag_id`) USING BTREE,
                                 INDEX `idx_pt_tag`(`tag_id` ASC) USING BTREE,
                                 CONSTRAINT `fk_pt_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                 CONSTRAINT `fk_pt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of problem_tags
-- ----------------------------
INSERT INTO `problem_tags` VALUES (2, 3);
INSERT INTO `problem_tags` VALUES (1, 10);
INSERT INTO `problem_tags` VALUES (1, 11);

-- ----------------------------
-- Table structure for problems
-- ----------------------------
DROP TABLE IF EXISTS `problems`;
CREATE TABLE `problems`  (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目ID',
                             `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目别名',
                             `problem_type` enum('coding','sql','shell','concurrency','interactive','output-only') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'coding' COMMENT '题目类型',
                             `difficulty_id` tinyint NOT NULL COMMENT '难度ID',
                             `category_id` smallint NULL DEFAULT NULL COMMENT '分类ID',
                             `creator_id` bigint NULL DEFAULT NULL COMMENT '创建者ID',
                             `solution_entry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '评测入口函数',
                             `time_limit_ms` int NULL DEFAULT NULL COMMENT '时间限制毫秒',
                             `memory_limit_kb` int NULL DEFAULT NULL COMMENT '内存限制KB',
                             `is_public` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否公开',
                             `lifecycle_status` enum('draft','in_review','approved','ready','published','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '生命周期状态',
                             `review_status` enum('pending','approved','rejected') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '审核状态',
                             `reviewed_by` bigint NULL DEFAULT NULL COMMENT '审核人',
                             `reviewed_at` timestamp NULL DEFAULT NULL COMMENT '审核时间',
                             `review_notes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核备注',
                             `submitted_for_review_at` timestamp NULL DEFAULT NULL COMMENT '提交审核时间',
                             `meta_json` json NULL COMMENT '题目元数据',
                             `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `active_dataset_id` bigint NULL DEFAULT NULL COMMENT '激活数据集ID',
                             PRIMARY KEY (`id`) USING BTREE,
                             UNIQUE INDEX `uk_problems_slug`(`slug` ASC) USING BTREE,
                             INDEX `idx_problems_visibility`(`is_public` ASC, `id` ASC) USING BTREE,
                             INDEX `idx_problems_lifecycle`(`lifecycle_status` ASC, `review_status` ASC) USING BTREE,
                             INDEX `idx_problems_diff`(`difficulty_id` ASC, `id` ASC) USING BTREE,
                             INDEX `idx_problems_cat`(`category_id` ASC, `id` ASC) USING BTREE,
                             INDEX `fk_problem_creator`(`creator_id` ASC) USING BTREE,
                             INDEX `fk_problem_active_dataset`(`active_dataset_id` ASC) USING BTREE,
                             INDEX `idx_problem_reviewer`(`reviewed_by` ASC) USING BTREE,
                             CONSTRAINT `fk_problem_active_dataset` FOREIGN KEY (`active_dataset_id`) REFERENCES `datasets` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
                             CONSTRAINT `fk_problem_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                             CONSTRAINT `fk_problem_creator` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
                             CONSTRAINT `fk_problem_difficulty` FOREIGN KEY (`difficulty_id`) REFERENCES `difficulties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                             CONSTRAINT `fk_problem_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of problems
-- ----------------------------
INSERT INTO `problems` (
    `id`,
    `slug`,
    `problem_type`,
    `difficulty_id`,
    `category_id`,
    `creator_id`,
    `solution_entry`,
    `time_limit_ms`,
    `memory_limit_kb`,
    `is_public`,
    `lifecycle_status`,
    `review_status`,
    `reviewed_by`,
    `reviewed_at`,
    `review_notes`,
    `submitted_for_review_at`,
    `meta_json`,
    `created_at`,
    `updated_at`,
    `active_dataset_id`
) VALUES (
             1,
             '1-two-sum',
             'coding',
             1,
             1,
             1,
             'twoSum',
             1000,
             262144,
             1,
             'published',
             'approved',
             1,
             '2025-10-17 18:06:54',
             '示例题目审核通过并发布',
             '2025-10-17 16:53:25',
             '{\"companies\": [\"Google\", \"Amazon\", \"Facebook\"], \"frequency\": 0.53, \"paid_only\": false, \"frontend_id\": 1, \"leetcode_style\": true}',
             '2025-10-17 16:53:25',
             '2025-10-17 18:06:54',
             1
         );
INSERT INTO `problems` (
    `id`,
    `slug`,
    `problem_type`,
    `difficulty_id`,
    `category_id`,
    `creator_id`,
    `solution_entry`,
    `time_limit_ms`,
    `memory_limit_kb`,
    `is_public`,
    `lifecycle_status`,
    `review_status`,
    `reviewed_by`,
    `reviewed_at`,
    `review_notes`,
    `submitted_for_review_at`,
    `meta_json`,
    `created_at`,
    `updated_at`,
    `active_dataset_id`
) VALUES (
             2,
             '344-reverse-string',
             'coding',
             1,
             1,
             1,
             'reverseString',
             1000,
             262144,
             1,
             'published',
             'approved',
             1,
             '2025-10-17 16:53:25',
             '示例题目审核通过并发布',
             '2025-10-17 16:53:25',
             '{\"companies\": [\"Microsoft\", \"Amazon\"], \"frequency\": 0.27, \"paid_only\": false, \"frontend_id\": 344, \"leetcode_style\": true}',
             '2025-10-17 16:53:25',
             '2025-10-17 16:53:25',
             2
         );

-- ----------------------------
-- Table structure for reactions
-- ----------------------------
DROP TABLE IF EXISTS `reactions`;
CREATE TABLE `reactions` (
                              `user_id` bigint NOT NULL COMMENT '用户ID',
                              `entity_type` enum('problem','comment','discussion') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体类型',
                              `entity_id` bigint NOT NULL COMMENT '实体ID',
                              `kind` enum('like','dislike','upvote','downvote') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '反馈类型',
                              `weight` tinyint NOT NULL DEFAULT 1 COMMENT '权重',
                              `source` enum('user','system','moderation') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '来源',
                              `metadata` json NULL COMMENT '附加信息',
                              `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`user_id`, `entity_type`, `entity_id`, `kind`) USING BTREE,
                              INDEX `idx_reactions_entity`(`entity_type` ASC, `entity_id` ASC, `kind` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of reactions
-- ----------------------------
INSERT INTO `reactions` (`user_id`, `entity_type`, `entity_id`, `kind`, `weight`, `source`, `metadata`, `created_at`, `updated_at`)
VALUES
    (1, 'problem', 1, 'like', 1, 'user', NULL, '2025-10-20 09:16:00', '2025-10-20 09:16:00'),
    (1, 'comment', 1, 'upvote', 1, 'system', JSON_OBJECT('reason', 'featured'), '2025-10-20 09:22:00', '2025-10-20 09:22:00');

-- ----------------------------
-- Table structure for sensitive_words
-- ----------------------------
DROP TABLE IF EXISTS `sensitive_words`;
CREATE TABLE `sensitive_words` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '敏感词ID',
                                   `word` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '敏感词',
                                   `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类',
                                   `level` enum('block','review','replace') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'review' COMMENT '处理等级',
                                   `replacement` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '替换词',
                                   `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '说明',
                                   `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                                   `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (`id`) USING BTREE,
                                   UNIQUE INDEX `uk_sensitive_words_word`(`word` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sensitive_words
-- ----------------------------
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `replacement`, `description`, `is_active`, `created_at`, `updated_at`)
VALUES
    (1, '违规示例', '违规', 'block', NULL, '命中即拦截', 1, '2025-10-20 08:00:00', '2025-10-20 08:00:00'),
    (2, '待审核词', '审核', 'review', NULL, '触发人工审核', 1, '2025-10-20 08:05:00', '2025-10-20 08:05:00'),
    (3, '敏感别称', '替换', 'replace', '***', '自动替换展示', 1, '2025-10-20 08:10:00', '2025-10-20 08:10:00');

-- ----------------------------
-- Table structure for moderation_tasks
-- ----------------------------
DROP TABLE IF EXISTS `moderation_tasks`;
CREATE TABLE `moderation_tasks` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核任务ID',
                                    `entity_type` enum('comment') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体类型',
                                    `entity_id` bigint NOT NULL COMMENT '实体ID',
                                    `status` enum('pending','in_review','approved','rejected','escalated') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '任务状态',
                                    `priority` tinyint NOT NULL DEFAULT 3 COMMENT '优先级(1最高)',
                                    `source` enum('auto','user_report','manual') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'auto' COMMENT '来源',
                                    `risk_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '风险等级',
                                    `reviewer_id` bigint NULL DEFAULT NULL COMMENT '审核人',
                                    `metadata` json NULL COMMENT '上下文信息',
                                    `notes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
                                    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `reviewed_at` timestamp NULL DEFAULT NULL COMMENT '审核完成时间',
                                    PRIMARY KEY (`id`) USING BTREE,
                                    INDEX `idx_moderation_tasks_status`(`status` ASC, `priority` ASC) USING BTREE,
                                    INDEX `idx_moderation_tasks_entity`(`entity_type` ASC, `entity_id` ASC) USING BTREE,
                                    CONSTRAINT `fk_moderation_tasks_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of moderation_tasks
-- ----------------------------
INSERT INTO `moderation_tasks` (`id`, `entity_type`, `entity_id`, `status`, `priority`, `source`, `risk_level`, `reviewer_id`, `metadata`, `notes`, `created_at`, `updated_at`, `reviewed_at`)
VALUES
    (1, 'comment', 2, 'pending', 2, 'auto', 'medium', NULL, JSON_OBJECT('hits', JSON_ARRAY('待审核词')), '命中敏感词等待审核', '2025-10-20 09:18:10', '2025-10-20 09:18:10', NULL),
    (2, 'comment', 1, 'approved', 3, 'user_report', 'low', 1, JSON_OBJECT('reporterId', 2), '复核通过', '2025-10-19 10:18:10', '2025-10-19 11:00:00', '2025-10-19 11:00:00');

-- ----------------------------
-- Table structure for moderation_actions
-- ----------------------------
DROP TABLE IF EXISTS `moderation_actions`;
CREATE TABLE `moderation_actions` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
                                      `task_id` bigint NOT NULL COMMENT '审核任务ID',
                                      `action` enum('created','assigned','approved','rejected','escalated','comment_updated') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作',
                                      `operator_id` bigint NULL DEFAULT NULL COMMENT '操作人',
                                      `remarks` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
                                      `context` json NULL COMMENT '上下文',
                                      `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      PRIMARY KEY (`id`) USING BTREE,
                                      INDEX `idx_moderation_actions_task`(`task_id` ASC, `created_at` ASC) USING BTREE,
                                      CONSTRAINT `fk_moderation_actions_task` FOREIGN KEY (`task_id`) REFERENCES `moderation_tasks` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                      CONSTRAINT `fk_moderation_actions_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of moderation_actions
-- ----------------------------
INSERT INTO `moderation_actions` (`id`, `task_id`, `action`, `operator_id`, `remarks`, `context`, `created_at`)
VALUES
    (1, 1, 'created', NULL, '系统自动创建审核任务', JSON_OBJECT('trigger', 'sensitive_word'), '2025-10-20 09:18:10'),
    (2, 2, 'approved', 1, '确认评论内容合规', JSON_OBJECT('decision', 'approve'), '2025-10-19 11:00:00');

-- ----------------------------
-- Table structure for role_permissions
-- ----------------------------
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions`  (
                                     `role_id` bigint NOT NULL COMMENT '角色ID',
                                     `perm_id` bigint NOT NULL COMMENT '权限ID',
                                     PRIMARY KEY (`role_id`, `perm_id`) USING BTREE,
                                     INDEX `idx_role_permissions_perm`(`perm_id` ASC) USING BTREE,
                                     CONSTRAINT `fk_role_permissions_perm` FOREIGN KEY (`perm_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                     CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role_permissions
-- ----------------------------
INSERT INTO `role_permissions` VALUES (2, 1);
INSERT INTO `role_permissions` VALUES (2, 3);
INSERT INTO `role_permissions` VALUES (3, 1);
INSERT INTO `role_permissions` VALUES (3, 2);
INSERT INTO `role_permissions` VALUES (3, 3);
INSERT INTO `role_permissions` VALUES (3, 4);
INSERT INTO `role_permissions` VALUES (3, 5);
INSERT INTO `role_permissions` VALUES (3, 6);
INSERT INTO `role_permissions` VALUES (3, 7);
INSERT INTO `role_permissions` VALUES (3, 9);
INSERT INTO `role_permissions` VALUES (3, 10);
INSERT INTO `role_permissions` VALUES (1, 1);
INSERT INTO `role_permissions` VALUES (1, 2);
INSERT INTO `role_permissions` VALUES (1, 3);
INSERT INTO `role_permissions` VALUES (1, 4);
INSERT INTO `role_permissions` VALUES (1, 5);
INSERT INTO `role_permissions` VALUES (1, 6);
INSERT INTO `role_permissions` VALUES (1, 7);
INSERT INTO `role_permissions` VALUES (1, 8);
INSERT INTO `role_permissions` VALUES (1, 9);
INSERT INTO `role_permissions` VALUES (1, 10);
INSERT INTO `role_permissions` VALUES (1, 11);

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
                          `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
                          `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
                          `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色备注',
                          `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`) USING BTREE,
                          UNIQUE INDEX `uk_roles_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of roles
-- ----------------------------
INSERT INTO `roles` VALUES (1, 'platform_admin', '平台管理员', '拥有全局账户与权限管理权限', '2025-10-15 08:40:00', '2025-10-17 14:15:41');
INSERT INTO `roles` VALUES (2, 'user', '普通用户', '默认权限', '2025-10-15 08:47:45', '2025-10-17 14:15:54');
INSERT INTO `roles` VALUES (3, 'admin', '管理员', '最高管理权限', '2025-10-17 12:28:09', '2025-10-17 14:15:41');

-- ----------------------------
-- Table structure for submission_artifacts
-- ----------------------------
DROP TABLE IF EXISTS `submission_artifacts`;
CREATE TABLE `submission_artifacts`  (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提交附件ID',
                                         `submission_id` bigint NOT NULL COMMENT '提交ID',
                                         `kind` enum('compile_log','run_log','stderr','stdout','diff','system') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '附件类型',
                                         `file_id` bigint NOT NULL COMMENT '文件ID',
                                         `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         PRIMARY KEY (`id`) USING BTREE,
                                         INDEX `idx_sa_submission`(`submission_id` ASC, `kind` ASC) USING BTREE,
                                         INDEX `fk_sa_file`(`file_id` ASC) USING BTREE,
                                         CONSTRAINT `fk_sa_file` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                         CONSTRAINT `fk_sa_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of submission_artifacts
-- ----------------------------
INSERT INTO `submission_artifacts` VALUES (1, 1, 'compile_log', 2, '2025-10-17 17:45:05');
INSERT INTO `submission_artifacts` VALUES (2, 2, 'stderr', 4, '2025-10-18 09:12:15');
INSERT INTO `submission_artifacts` VALUES (3, 3, 'compile_log', 6, '2025-10-18 09:31:10');

-- ----------------------------
-- Table structure for submission_tests
-- ----------------------------
DROP TABLE IF EXISTS `submission_tests`;
CREATE TABLE `submission_tests`  (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '测试结果ID',
                                     `submission_id` bigint NOT NULL COMMENT '提交ID',
                                     `testcase_id` bigint NOT NULL COMMENT '测试用例ID',
                                     `group_id` bigint NOT NULL COMMENT '测试组ID',
                                     `verdict` enum('AC','WA','TLE','MLE','RE','OLE','PE','SKIP','IE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '判题结果',
                                     `time_ms` int NULL DEFAULT NULL COMMENT '耗时毫秒',
                                     `memory_kb` int NULL DEFAULT NULL COMMENT '内存消耗KB',
                                     `score` int NULL DEFAULT NULL COMMENT '得分',
                                     `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '附加信息',
                                     PRIMARY KEY (`id`) USING BTREE,
                                     UNIQUE INDEX `uk_submission_test`(`submission_id` ASC, `testcase_id` ASC) USING BTREE,
                                     INDEX `idx_st_group`(`group_id` ASC) USING BTREE,
                                     INDEX `fk_st_testcase`(`testcase_id` ASC) USING BTREE,
                                     CONSTRAINT `fk_st_group` FOREIGN KEY (`group_id`) REFERENCES `testcase_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                     CONSTRAINT `fk_st_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                     CONSTRAINT `fk_st_testcase` FOREIGN KEY (`testcase_id`) REFERENCES `testcases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of submission_tests
-- ----------------------------
INSERT INTO `submission_tests` VALUES (1, 1, 1, 1, 'AC', 5, 1024, 10, NULL);
INSERT INTO `submission_tests` VALUES (2, 1, 2, 1, 'AC', 6, 1100, 10, NULL);
INSERT INTO `submission_tests` VALUES (3, 1, 3, 2, 'AC', 7, 1152, 10, NULL);
INSERT INTO `submission_tests` VALUES (4, 1, 4, 2, 'AC', 8, 1200, 10, NULL);
INSERT INTO `submission_tests` VALUES (5, 2, 1, 1, 'AC', 6, 1024, 10, NULL);
INSERT INTO `submission_tests` VALUES (6, 2, 2, 1, 'WA', 7, 1100, 0, '输出与期望不一致');
INSERT INTO `submission_tests` VALUES (7, 2, 3, 2, 'SKIP', NULL, NULL, 0, '依赖前置用例失败');
INSERT INTO `submission_tests` VALUES (8, 2, 4, 2, 'SKIP', NULL, NULL, 0, '依赖前置用例失败');

-- ----------------------------
-- Table structure for submissions
-- ----------------------------
DROP TABLE IF EXISTS `submissions`;
CREATE TABLE `submissions`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提交ID',
                                `user_id` bigint NOT NULL COMMENT '提交用户ID',
                                `problem_id` bigint NOT NULL COMMENT '题目ID',
                                `dataset_id` bigint NOT NULL COMMENT '使用数据集ID',
                                `language_id` smallint NOT NULL COMMENT '编程语言ID',
                                `source_file_id` bigint NOT NULL COMMENT '源代码文件ID',
                                `code_bytes` int NULL DEFAULT NULL COMMENT '代码大小字节',
                                `verdict` enum('PD','AC','WA','TLE','MLE','RE','CE','OLE','PE','IE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PD' COMMENT '最终判题结果',
                                `score` int NULL DEFAULT NULL COMMENT '得分',
                                `time_ms` int NULL DEFAULT NULL COMMENT '总耗时毫秒',
                                `memory_kb` int NULL DEFAULT NULL COMMENT '内存消耗KB',
                                `judge_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '评测消息',
                                `ip_addr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '提交IP',
                                `contest_id` bigint NULL DEFAULT NULL COMMENT '所属比赛ID',
                                `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                INDEX `idx_submissions_user`(`user_id` ASC, `created_at` ASC) USING BTREE,
                                INDEX `idx_submissions_problem`(`problem_id` ASC, `created_at` ASC) USING BTREE,
                                INDEX `idx_submissions_contest`(`contest_id` ASC, `created_at` ASC) USING BTREE,
                                INDEX `idx_submissions_verdict`(`verdict` ASC, `created_at` ASC) USING BTREE,
                                INDEX `fk_sub_dataset`(`dataset_id` ASC) USING BTREE,
                                INDEX `fk_sub_language`(`language_id` ASC) USING BTREE,
                                INDEX `fk_sub_source`(`source_file_id` ASC) USING BTREE,
                                CONSTRAINT `fk_sub_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `datasets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                CONSTRAINT `fk_sub_language` FOREIGN KEY (`language_id`) REFERENCES `languages` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                CONSTRAINT `fk_sub_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                                CONSTRAINT `fk_sub_source` FOREIGN KEY (`source_file_id`) REFERENCES `files` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                CONSTRAINT `fk_sub_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of submissions
-- ----------------------------
INSERT INTO `submissions` VALUES (1, 1, 1, 1, 1, 1, 428, 'AC', 100, 12, 4096, '所有测试用例通过', '127.0.0.1', 1, '2025-10-17 17:45:00');
INSERT INTO `submissions` VALUES (2, 1, 1, 1, 2, 3, 312, 'WA', 40, 68, 8192, '样例 2 输出不符', '127.0.0.1', NULL, '2025-10-18 09:10:00');
INSERT INTO `submissions` VALUES (3, 1, 2, 1, 3, 5, 640, 'PD', NULL, NULL, NULL, NULL, '127.0.0.1', NULL, '2025-10-18 09:30:00');

-- ----------------------------
-- Table structure for tags
-- ----------------------------
DROP TABLE IF EXISTS `tags`;
CREATE TABLE `tags`  (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
                         `slug` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签别名',
                         `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
                         `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`) USING BTREE,
                         UNIQUE INDEX `uk_tags_slug`(`slug` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tags
-- ----------------------------
INSERT INTO `tags` VALUES (3, 'string', 'String', '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `tags` VALUES (10, 'array', 'Array', '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `tags` VALUES (11, 'hash-table', 'Hash Table', '2025-10-17 16:53:25', '2025-10-17 16:53:25');

-- ----------------------------
-- Table structure for testcase_groups
-- ----------------------------
DROP TABLE IF EXISTS `testcase_groups`;
CREATE TABLE `testcase_groups`  (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '测试组ID',
                                    `dataset_id` bigint NOT NULL COMMENT '所属数据集ID',
                                    `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试组名称',
                                    `is_sample` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为样例',
                                    `weight` int NOT NULL DEFAULT 1 COMMENT '测试组权重',
                                    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`) USING BTREE,
                                    INDEX `idx_tcg_dataset`(`dataset_id` ASC, `is_sample` ASC) USING BTREE,
                                    CONSTRAINT `fk_tcg_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `datasets` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of testcase_groups
-- ----------------------------
INSERT INTO `testcase_groups` VALUES (1, 1, 'samples', 1, 1, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcase_groups` VALUES (2, 1, 'hidden', 0, 1, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcase_groups` VALUES (3, 2, 'samples', 1, 1, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcase_groups` VALUES (4, 2, 'hidden', 0, 1, '2025-10-17 16:53:25', '2025-10-17 16:53:25');

-- ----------------------------
-- Table structure for testcases
-- ----------------------------
DROP TABLE IF EXISTS `testcases`;
CREATE TABLE `testcases`  (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '测试用例ID',
                              `group_id` bigint NOT NULL COMMENT '测试组ID',
                              `order_index` int NOT NULL DEFAULT 0 COMMENT '用例顺序',
                              `input_file_id` bigint NULL DEFAULT NULL COMMENT '输入文件ID',
                              `output_file_id` bigint NULL DEFAULT NULL COMMENT '输出文件ID',
                              `input_json` json NULL COMMENT '输入JSON',
                              `output_json` json NULL COMMENT '输出JSON',
                              `output_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '输出类型',
                              `score` int NOT NULL DEFAULT 10 COMMENT '用例分值',
                              `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`) USING BTREE,
                              INDEX `idx_tc_group_order`(`group_id` ASC, `order_index` ASC) USING BTREE,
                              INDEX `fk_tc_input_file`(`input_file_id` ASC) USING BTREE,
                              INDEX `fk_tc_output_file`(`output_file_id` ASC) USING BTREE,
                              CONSTRAINT `fk_tc_group` FOREIGN KEY (`group_id`) REFERENCES `testcase_groups` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                              CONSTRAINT `fk_tc_input_file` FOREIGN KEY (`input_file_id`) REFERENCES `files` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
                              CONSTRAINT `fk_tc_output_file` FOREIGN KEY (`output_file_id`) REFERENCES `files` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of testcases
-- ----------------------------
INSERT INTO `testcases` VALUES (1, 1, 1, NULL, NULL, '{\"nums\": [2, 7, 11, 15], \"target\": 9}', '[0, 1]', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (2, 1, 2, NULL, NULL, '{\"nums\": [3, 2, 4], \"target\": 6}', '[1, 2]', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (3, 2, 1, NULL, NULL, '{\"nums\": [-1, -2, -3, -4, -5], \"target\": -8}', '[2, 4]', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (4, 2, 2, NULL, NULL, '{\"nums\": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], \"target\": 19}', '[8, 9]', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (5, 3, 1, NULL, NULL, '{\"s\": \"hello\"}', '\"olleh\"', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (6, 3, 2, NULL, NULL, '{\"s\": \"ab\"}', '\"ba\"', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (7, 4, 1, NULL, NULL, '{\"s\": \"racecar\"}', '\"racecar\"', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');
INSERT INTO `testcases` VALUES (8, 4, 2, NULL, NULL, '{\"s\": \"OpenAI GPT\"}', '\"TPG IAnepO\"', 'json', 10, '2025-10-17 16:53:25', '2025-10-17 16:53:25');

-- ----------------------------
-- Table structure for user_roles
-- ----------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles`  (
                               `user_id` bigint NOT NULL COMMENT '用户ID',
                               `role_id` bigint NOT NULL COMMENT '角色ID',
                               `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
                               PRIMARY KEY (`user_id`, `role_id`) USING BTREE,
                               INDEX `idx_user_roles_role`(`role_id` ASC) USING BTREE,
                               CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
                               CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_roles
-- ----------------------------
INSERT INTO `user_roles` VALUES (1, 2, '2025-10-17 13:13:45');
INSERT INTO `user_roles` VALUES (1, 3, '2025-10-17 13:13:57');
INSERT INTO `user_roles` VALUES (1, 1, '2025-10-20 09:30:00');

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                          `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
                          `email` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '电子邮箱',
                          `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
                          `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像地址',
                          `bio` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '个人简介',
                          `status` tinyint NOT NULL DEFAULT 1 COMMENT '账户状态',
                          `last_login_at` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
                          `last_login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最后登录IP',
                          `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`) USING BTREE,
                          UNIQUE INDEX `uk_users_username`(`username` ASC) USING BTREE,
                          UNIQUE INDEX `uk_users_email`(`email` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (1, 'DavidHLP', 'lysf15520112973@163.com', '$2a$12$YGHyAEo6oMy/uZzrgY.MBe2UP40qD62lMGlUU4UgH6dhwRE4r3D/W', NULL, NULL, 1, '2025-10-17 18:06:33', '127.0.0.1', '2025-10-15 08:47:45', '2025-10-17 18:06:33');

-- ----------------------------
-- View structure for vw_problem_stats
-- ----------------------------
DROP VIEW IF EXISTS `vw_problem_stats`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `vw_problem_stats` AS select `p`.`id` AS `problem_id`,count(`s`.`id`) AS `submission_count`,sum((case when (`s`.`verdict` = 'AC') then 1 else 0 end)) AS `solved_count`,round((case when (count(`s`.`id`) = 0) then NULL else ((sum((case when (`s`.`verdict` = 'AC') then 1 else 0 end)) / count(`s`.`id`)) * 100) end),2) AS `acceptance_rate`,max(`s`.`created_at`) AS `last_submission_at` from (`problems` `p` left join `submissions` `s` on((`s`.`problem_id` = `p`.`id`))) group by `p`.`id`;

-- ----------------------------
-- View structure for vw_user_problem_best
-- ----------------------------
DROP VIEW IF EXISTS `vw_user_problem_best`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `vw_user_problem_best` AS select `s`.`user_id` AS `user_id`,`s`.`problem_id` AS `problem_id`,min((case when (`s`.`verdict` = 'AC') then `s`.`created_at` else NULL end)) AS `first_ac_time`,max(`s`.`score`) AS `best_score` from `submissions` `s` group by `s`.`user_id`,`s`.`problem_id`;

SET FOREIGN_KEY_CHECKS = 1;
