-- V25__collections_seed.sql
-- Phase 18: Collections Seed (V25)
-- 51 platform collections organized by difficulty, topic, company, contest type
-- Each collection has ≥3 items; total ~180 collection_items
-- FK targets: list-essentials, list-intervals, list-sliding-window, list-interview-100, list-database, list-concurrency, list-graph-advanced

SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;

-- ============================================================================
-- DIFFICULTY COLLECTIONS (3) — target_type='PROBLEM'
-- ============================================================================

-- Easy: 算法入门精选 (Star, emerald)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-easy-001','user-alex','算法入门精选','适合算法初学者的基础题解，涵盖数组遍历、哈希表基础等核心模式。','Star','emerald',1,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-easy-001','col-easy-001','1','PROBLEM',0,'两数之和',NOW(3)),
('ci-easy-002','col-easy-001','2','PROBLEM',1,'反转链表',NOW(3)),
('ci-easy-003','col-easy-001','10','PROBLEM',2,'爬楼梯',NOW(3));

-- Medium: 算法进阶挑战 (Flame, amber)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-medium-001','user-alex','算法进阶挑战','适合有一定基础的开发者，涵盖二叉树、图遍历、动态规划等进阶题型。','Flame','amber',2,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-medium-001','col-medium-001','3','PROBLEM',0,'三数之和',NOW(3)),
('ci-medium-002','col-medium-001','4','PROBLEM',1,'括号生成',NOW(3)),
('ci-medium-003','col-medium-001','5','PROBLEM',2,'二叉树的中序遍历',NOW(3));

-- Hard: 专家级精选 (Zap, rose)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-hard-001','user-alex','专家级精选','面向高级工程师的挑战题目，包含 Hard 难度真题和高难度竞赛题。','Zap','rose',3,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-hard-001','col-hard-001','8','PROBLEM',0,'接雨水',NOW(3)),
('ci-hard-002','col-hard-001','9','PROBLEM',1,'正则表达式匹配',NOW(3)),
('ci-hard-003','col-hard-001','13','PROBLEM',2,'滑动窗口最大值',NOW(3));

-- ============================================================================
-- TOPIC/TAG COLLECTIONS (18) — target_type='PROBLEM_LIST'
-- Each has 3-4 items referencing problem lists
-- ============================================================================

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-array-001','user-david','数组专项训练','数组遍历、前缀和、旋转数组等数组模式专项练习。','Square','sky',10,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-array-001','col-array-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-array-002','col-array-001','list-intervals','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-array-003','col-array-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-string-001','user-david','字符串处理进阶','字符串匹配、替换，子串问题专项训练。','Type','teal',11,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-string-001','col-string-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-string-002','col-string-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-string-003','col-string-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-two-pointer-001','user-sara','双指针技巧','快慢指针、左右指针、对撞型双指针专项。','Share2','orange',12,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-tp-001','col-two-pointer-001','list-sliding-window','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-tp-002','col-two-pointer-001','list-intervals','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-tp-003','col-two-pointer-001','list-interview-100','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-hash-001','user-sara','哈希表专项','哈希表基础，去重、计数问题专项训练。','Hash','cyan',13,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-hash-001','col-hash-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-hash-002','col-hash-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-hash-003','col-hash-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-tree-001','user-chen','二叉树算法训练','二叉树遍历、递归、路径相关问题专项。','GitBranch','emerald',14,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-tree-001','col-tree-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-tree-002','col-tree-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-tree-003','col-tree-001','list-graph-advanced','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-btree-001','user-chen','二叉搜索树专项','BST 查找、插入、平衡等 BST 专项训练。','GitBranch','green',15,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-btree-001','col-btree-001','list-graph-advanced','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-btree-002','col-btree-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-btree-003','col-btree-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-graph-001','user-alex','图论算法入门','BFS、DFS、图的遍历基础专项训练。','Share2','violet',16,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-graph-001','col-graph-001','list-graph-advanced','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-graph-002','col-graph-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-graph-003','col-graph-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-dp-001','user-alex','动态规划专项','一维 DP、二维 DP、状态压缩 DP 专项训练。','Layers','amber',17,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-dp-001','col-dp-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-dp-002','col-dp-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-dp-003','col-dp-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-greedy-001','user-david','贪心算法训练','区间选择、区间调度，最优子结构贪心策略。','Zap','orange',18,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-greedy-001','col-greedy-001','list-intervals','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-greedy-002','col-greedy-001','list-sliding-window','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-greedy-003','col-greedy-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-binary-search-001','user-sara','二分查找专项','标准二分、左侧边界、右侧边界二分查找训练。','Search','sky',19,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-bs-001','col-binary-search-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-bs-002','col-binary-search-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-bs-003','col-binary-search-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-sliding-window-001','user-sara','滑动窗口技巧','固定窗口、可变窗口、窗口收缩策略专项训练。','ListOrdered','teal',20,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-sw-001','col-sliding-window-001','list-sliding-window','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-sw-002','col-sliding-window-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-sw-003','col-sliding-window-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-stack-001','user-chen','栈结构专项','单调栈、括号匹配、表达式求值等栈相关问题。','ListOrdered','cyan',21,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-stack-001','col-stack-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-stack-002','col-stack-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-stack-003','col-stack-001','list-intervals','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-queue-001','user-chen','队列与BFS','队列结构与广度优先搜索结合的算法训练。','ListOrdered','indigo',22,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-queue-001','col-queue-001','list-graph-advanced','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-queue-002','col-queue-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-queue-003','col-queue-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-heap-001','user-alex','堆与优先级队列','Top-K、中位数、堆排序等堆结构专项训练。','ArrowUpDown','rose',23,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-heap-001','col-heap-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-heap-002','col-heap-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-heap-003','col-heap-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-backtrack-001','user-david','回溯算法训练','子集、排列、组合、岛屿类回溯问题专项。','Undo2','violet',24,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-bt-001','col-backtrack-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-bt-002','col-backtrack-001','list-graph-advanced','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-bt-003','col-backtrack-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-dc-001','user-sara','分治算法专项','归并排序、快速幂、最近点对等分治策略训练。','Layers','emerald',25,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-dc-001','col-dc-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-dc-002','col-dc-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-dc-003','col-dc-001','list-graph-advanced','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-bit-001','user-chen','位运算技巧','位操作基础、掩码、位图与状态压缩训练。','Hash','slate',26,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-bit-001','col-bit-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-bit-002','col-bit-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-bit-003','col-bit-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-math-001','user-alex','数学与概率','概率计算、数论基础、随机化算法训练。','Hash','lime',27,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-math-001','col-math-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-math-002','col-math-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-math-003','col-math-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

-- ============================================================================
-- COMPANY/USE CASE COLLECTIONS (15) — target_type='PROBLEM_LIST'
-- Each has 3-4 items referencing problem lists
-- ============================================================================

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-fang-001','user-alex','FANG 面试算法合集','Facebook、Amazon、Netflix、Google 高频面试算法题。','Building2','violet',30,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-fang-001','col-fang-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-fang-002','col-fang-001','list-sliding-window','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-fang-003','col-fang-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-flagm-001','user-david','FLAGM 面试高频','Facebook、LinkedIn、Google、Microsoft 高频面试题汇总。','Building2','purple',31,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-flagm-001','col-flagm-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-flagm-002','col-flagm-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-flagm-003','col-flagm-001','list-graph-advanced','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-batm-001','user-sara','国内大厂面试合集','阿里巴巴、腾讯、字节跳动、美团等国内大厂面试高频题。','Building2','red',32,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-batm-001','col-batm-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-batm-002','col-batm-001','list-graph-advanced','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-batm-003','col-batm-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-startup-001','user-chen','Startup 练手题集','适合 warmup 和面试准备的中小厂常见题型。','Building2','orange',33,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-startup-001','col-startup-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-startup-002','col-startup-001','list-intervals','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-startup-003','col-startup-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-contest-basic-001','user-alex','竞赛入门题集','适合准备 ACM 新生赛和大学编程竞赛的基础题集。','Trophy','amber',34,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-cb-001','col-contest-basic-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-cb-002','col-contest-basic-001','list-intervals','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-cb-003','col-contest-basic-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-acm-001','user-david','ACM 竞赛进阶','面向有一定竞赛经验的选手，高难度竞赛题目训练。','Trophy','rose',35,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-acm-001','col-acm-001','list-graph-advanced','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-acm-002','col-acm-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-acm-003','col-acm-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-huawei-001','user-sara','华为技术面试题库','华为面试高频算法题，覆盖数据结构与算法核心。','Building2','sky',36,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-huawei-001','col-huawei-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-huawei-002','col-huawei-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-huawei-003','col-huawei-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-tencent-001','user-chen','腾讯社招面试算法','腾讯社会招聘技术面试算法题库。','Building2','teal',37,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-tencent-001','col-tencent-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-tencent-002','col-tencent-001','list-sliding-window','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-tencent-003','col-tencent-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-alibaba-001','user-alex','阿里巴巴算法面试','阿里巴巴技术岗位算法面试高频题目。','Building2','orange',38,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-alibaba-001','col-alibaba-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-alibaba-002','col-alibaba-001','list-graph-advanced','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-alibaba-003','col-alibaba-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-bytedance-001','user-david','字节跳动面试题库','字节跳动技术面试算法高频题库。','Building2','blue',39,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-bd-001','col-bytedance-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-bd-002','col-bytedance-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-bd-003','col-bytedance-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-meituan-001','user-sara','美团技术面试高频','美团点评技术岗位面试高频算法题。','Building2','yellow',40,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-mt-001','col-meituan-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-mt-002','col-meituan-001','list-sliding-window','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-mt-003','col-meituan-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-jd-001','user-chen','京东算法面试题','京东技术面试算法题目精选。','Building2','red',41,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-jd-001','col-jd-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-jd-002','col-jd-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-jd-003','col-jd-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-microsoft-001','user-alex','微软面试算法题库','Microsoft 面试高频算法题目合集。','Building2','sky',42,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-ms-001','col-microsoft-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-ms-002','col-microsoft-001','list-graph-advanced','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-ms-003','col-microsoft-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-google-001','user-david','Google 面试算法题','Google 技术面试高频算法题库。','Building2','emerald',43,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-google-001','col-google-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-google-002','col-google-001','list-sliding-window','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-google-003','col-google-001','list-graph-advanced','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-meta-001','user-sara','Meta 面试高频题','Meta (Facebook) 技术岗位面试算法高频题目。','Building2','indigo',44,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-meta-001','col-meta-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-meta-002','col-meta-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-meta-003','col-meta-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

-- ============================================================================
-- CONTEST TYPE COLLECTIONS (5) — target_type='PROBLEM_LIST'
-- ============================================================================

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-hot100-001','user-alex','LeetCode Hot 100 精选','当前最热门的 100 道算法题，高频面试必备。','Flame','amber',50,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-hot100-001','col-hot100-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-hot100-002','col-hot100-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-hot100-003','col-hot100-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-jianzhioffer-001','user-david','剑指 Offer 专项','面试算法书《剑指 Offer》题目专项训练。','Trophy','rose',51,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-jz-001','col-jianzhioffer-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-jz-002','col-jianzhioffer-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-jz-003','col-jianzhioffer-001','list-graph-advanced','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-weekly-001','user-sara','LeetCode 周赛压轴题','LeetCode 周赛最后一题的解题技巧与训练。','Trophy','violet',52,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-weekly-001','col-weekly-001','list-graph-advanced','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-weekly-002','col-weekly-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-weekly-003','col-weekly-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-biweekly-001','user-chen','双周赛压轴题','LeetCode 双周赛最后一题挑战训练。','Trophy','teal',53,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-biweekly-001','col-biweekly-001','list-graph-advanced','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-biweekly-002','col-biweekly-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-biweekly-003','col-biweekly-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-interview100-001','user-alex','面试算法 100 题','面试准备必刷的 100 道核心算法题。','Trophy','amber',54,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-i100-001','col-interview100-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-i100-002','col-interview100-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-i100-003','col-interview100-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

-- ============================================================================
-- FEATURED LIST COLLECTIONS (4) — direct references to V15 lists
-- ============================================================================

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-featured-interview-100','user-alex','面试算法高频 100 题','平台精选面试高频算法题 100 道，全面覆盖面试核心考点。','Trophy','amber',60,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-fi100-001','col-featured-interview-100','list-interview-100','PROBLEM_LIST',0,'面试高频榜首题库',NOW(3)),
('ci-fi100-002','col-featured-interview-100','list-essentials','PROBLEM_LIST',1,'核心基础题',NOW(3)),
('ci-fi100-003','col-featured-interview-100','list-sliding-window','PROBLEM_LIST',2,'滑动窗口专题',NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-featured-database','user-david','数据库专项训练题库','SQL 查询优化、索引设计、事务处理全覆盖。','Database','sky',61,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-fdb-001','col-featured-database','list-database','PROBLEM_LIST',0,'数据库专项训练',NOW(3)),
('ci-fdb-002','col-featured-database','list-interview-100','PROBLEM_LIST',1,'综合面试题',NOW(3)),
('ci-fdb-003','col-featured-database','list-essentials','PROBLEM_LIST',2,'基础题',NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-featured-concurrency','user-sara','并发编程专项题库','多线程、锁机制、并发安全从零开始系统训练。','Code2','emerald',62,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-fconc-001','col-featured-concurrency','list-concurrency','PROBLEM_LIST',0,'并发编程入门',NOW(3)),
('ci-fconc-002','col-featured-concurrency','list-interview-100','PROBLEM_LIST',1,'面试综合',NOW(3)),
('ci-fconc-003','col-featured-concurrency','list-graph-advanced','PROBLEM_LIST',2,'图论进阶',NOW(3));

INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-featured-graph','user-chen','图论进阶专项题库','DFS/BFS 深入，最短路、网络流全覆盖。','ArrowUpDown','slate',63,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-fgraph-001','col-featured-graph','list-graph-advanced','PROBLEM_LIST',0,'图论进阶专项',NOW(3)),
('ci-fgraph-002','col-featured-graph','list-interview-100','PROBLEM_LIST',1,'面试高频',NOW(3)),
('ci-fgraph-003','col-featured-graph','list-essentials','PROBLEM_LIST',2,'基础题',NOW(3));

-- Additional collections to reach target of 48

-- Top Problems 热题 (Flame, rose)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-top-problems-001','user-alex','平台最热题目精选','根据AC率、提交量精选的最热门题目合集。','Flame','rose',64,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-top-001','col-top-problems-001','list-interview-100','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-top-002','col-top-problems-001','list-essentials','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-top-003','col-top-problems-001','list-sliding-window','PROBLEM_LIST',2,NULL,NOW(3));

-- Algorithms Overview 算法全景 (Layers, indigo)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-algo-overview-001','user-david','算法全景入门','覆盖主要算法思想和数据结构的核心题目，系统梳理算法体系。','Layers','indigo',65,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-ao-001','col-algo-overview-001','list-essentials','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-ao-002','col-algo-overview-001','list-interview-100','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-ao-003','col-algo-overview-001','list-graph-advanced','PROBLEM_LIST',2,NULL,NOW(3));

-- Daily Challenge 每日一练 (Zap, amber)
INSERT INTO `collections` (`id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`, `created_at`, `updated_at`) VALUES
('col-daily-001','user-sara','每日算法挑战','每天坚持练习一套算法题，稳步提升编程能力。','Zap','amber',66,0,NOW(3),NOW(3));
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`, `created_at`) VALUES
('ci-daily-001','col-daily-001','list-intervals','PROBLEM_LIST',0,NULL,NOW(3)),
('ci-daily-002','col-daily-001','list-sliding-window','PROBLEM_LIST',1,NULL,NOW(3)),
('ci-daily-003','col-daily-001','list-essentials','PROBLEM_LIST',2,NULL,NOW(3)),
('ci-daily-004','col-daily-001','list-interview-100','PROBLEM_LIST',3,NULL,NOW(3));

COMMIT;
SET FOREIGN_KEY_CHECKS=1;
