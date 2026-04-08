-- Featured Problem Lists with enhanced data
-- This migration updates existing featured lists with better descriptions
-- and adds new featured problem lists with banner configurations

SET FOREIGN_KEY_CHECKS=0;

-- Update existing featured lists to have better descriptions
UPDATE `problem_lists` SET `description` = '必知必会的算法模式，涵盖数组、哈希表、双指针等核心内容。' WHERE `id` = 'list-essentials';
UPDATE `problem_lists` SET `description` = '扫描线、合并区间、排序技巧，竞赛常见题型。' WHERE `id` = 'list-intervals';
UPDATE `problem_lists` SET `description` = '固定窗口与可变窗口，字符串处理利器。' WHERE `id` = 'list-sliding-window';

-- Insert new featured lists
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES
('list-interview-100','算法面试高频 100','面试中最常出现的算法题，精选 100 道高效准备。','user-alex',1,NOW(3),NOW(3),1,'面试','Trophy','amber',4),
('list-database','数据库专项训练','SQL 查询优化、索引设计、事务处理一网打尽。','user-david',1,NOW(3),NOW(3),1,'数据库','Database','sky',5),
('list-concurrency','并发编程入门','多线程、锁机制、并发安全从零开始。','user-sara',1,NOW(3),NOW(3),1,'并发','Code2','emerald',6),
('list-graph-advanced','图论进阶','DFS/BFS 深入，最短路、网络流全覆盖。','user-chen',1,NOW(3),NOW(3),1,'图论','ArrowUpDown','slate',7);

-- Add problem relations for new lists
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES
('list-interview-100',1,1,NOW(3)),
('list-interview-100',2,2,NOW(3)),
('list-interview-100',3,3,NOW(3)),
('list-database',6,1,NOW(3)),
('list-database',7,2,NOW(3)),
('list-database',8,3,NOW(3)),
('list-concurrency',8,1,NOW(3)),
('list-concurrency',4,2,NOW(3)),
('list-concurrency',1,3,NOW(3)),
('list-graph-advanced',5,1,NOW(3)),
('list-graph-advanced',3,2,NOW(3)),
('list-graph-advanced',4,3,NOW(3));

SET FOREIGN_KEY_CHECKS=1;
