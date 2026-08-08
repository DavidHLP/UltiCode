-- Seed four top-level problem category tags. The category filter in
-- ProblemServiceImpl.listProblems resolves the bare category value sent by
-- the frontend (e.g. "algorithms") to the matching problem_tags row via its
-- namespaced slug, joined through problem_tag_relations:
--   WHERE pt.slug = CONCAT('problem-category-', <category>)
-- (see backend-app/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java).
--
-- Front-end constants live at console/src/constants/problem-categories.ts
-- (values: all / algorithms / database / shell / concurrency). The "all"
-- value is short-circuited in the service layer and does not need a tag.
--
-- Slugs are namespaced (problem-category-*) so they cannot collide with the
-- existing fine-grained tag slugs (array / hash-table / linked-list / ...).
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`)
VALUES
('tag-algorithms',  '算法',     'problem-category-algorithms',  '#22d3ee', '算法题: 数组、链表、字符串、动态规划、图论等',         0),
('tag-database',    '数据库',   'problem-category-database',    '#a78bfa', '数据库题: SQL 查询、索引、事务、锁、范式等',         0),
('tag-shell',       'Shell',    'problem-category-shell',       '#facc15', 'Shell / Linux 命令: 文本处理、管道、脚本、系统调用等', 0),
('tag-concurrency', '并发',     'problem-category-concurrency', '#fb7185', '并发题: 线程、锁、信号量、生产者/消费者、Actor 等',   0)
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `color` = VALUES(`color`),
  `description` = VALUES(`description`);

-- Backfill existing seeded problems (id 1, 2, 3, 4, 6, 7) into the
-- "algorithms" category. The demo corpus is intentionally all algorithmic
-- (两数之和 / 两数相加 / 无重复字符的最长子串 / 寻找两个正序数组的中位数 /
-- 反转链表 / 合并K个升序链表), so a single category bucket is correct.
-- Problems in other categories (database / shell / concurrency) will be
-- created through the admin form once non-algorithmic content is added.
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`)
VALUES
(1, 'tag-algorithms'),
(2, 'tag-algorithms'),
(3, 'tag-algorithms'),
(4, 'tag-algorithms'),
(6, 'tag-algorithms'),
(7, 'tag-algorithms')
ON DUPLICATE KEY UPDATE
  `problem_id` = VALUES(`problem_id`);

-- Recompute usage_count for the new category tags so the admin UI shows
-- the correct number without a full re-seed. Matches the pattern used in
-- V20260603_120000__Seed_Problems_Test_Data.sql.
UPDATE `problem_tags` pt
LEFT JOIN (
  SELECT `tag_id`, COUNT(*) AS cnt
  FROM `problem_tag_relations`
  GROUP BY `tag_id`
) rel ON pt.id = rel.tag_id
SET pt.`usage_count` = COALESCE(rel.cnt, 0),
    pt.`updated_at` = NOW(3)
WHERE pt.id IN ('tag-algorithms', 'tag-database', 'tag-shell', 'tag-concurrency');
