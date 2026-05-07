SET FOREIGN_KEY_CHECKS=0;

UPDATE `problem_lists` SET `name` = '并发编程入门', `description` = '多线程、锁机制、并发安全从零开始。' WHERE `id` = 'list-concurrency';

UPDATE `problem_lists` SET `description` = '必知必会的算法模式，涵盖数组、哈希表、双指针等核心内容。' WHERE `id` = 'list-essentials';

UPDATE `problem_lists` SET `description` = 'DFS/BFS 深入，最短路、网络流全覆盖。' WHERE `id` = 'list-graph-advanced';

UPDATE `problem_lists` SET `name` = '图 DFS/BFS 热身' WHERE `id` = 'list-graph-dfs';

UPDATE `problem_lists` SET `name` = '难题基准', `description` = '精选难题，用于面试准备和竞赛训练。' WHERE `id` = 'list-hard-bench';

UPDATE `problem_lists` SET `description` = '扫描线、合并区间、排序技巧，竞赛常见题型。' WHERE `id` = 'list-intervals';

UPDATE `problem_lists` SET `name` = '算法面试高频 100' WHERE `id` = 'list-interview-100';

UPDATE `problem_lists` SET `description` = '固定窗口与可变窗口，字符串处理利器。' WHERE `id` = 'list-sliding-window';

SET FOREIGN_KEY_CHECKS=1;