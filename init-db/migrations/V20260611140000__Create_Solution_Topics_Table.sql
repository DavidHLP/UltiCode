-- V20260611140000__Create_Solution_Topics_Table.sql
-- Create solution_topics table for Solution module topic taxonomy (BUG-01 fix).
-- Frontend console/src/api/topic.ts::fetchSolutionTopics() already consumes this
-- endpoint but backend was missing; see docs/search-topic-api-test-report-2026-06-11.md.

CREATE TABLE solution_topics (
    id          VARCHAR(40)   NOT NULL,
    name        VARCHAR(64)   NOT NULL,
    slug        VARCHAR(64)   NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    is_active   TINYINT UNSIGNED NOT NULL DEFAULT 1,
    is_deleted  TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_solution_topics_slug (slug),
    -- L3 (review): renamed from idx_solution_topics_active → 3-col composite index,
    -- full column list in name for clarity per 05-mysql-database.md convention.
    KEY idx_solution_topics_active_deleted_sort (is_active, is_deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- L4 (review): "8 common algorithm topics" matches the 8 INSERT rows below.
-- Seed data: 8 common algorithm topics (deterministic IDs, no auto-increment needed)
INSERT INTO solution_topics (id, name, slug, sort_order) VALUES
    ('topic-greedy',             '贪心算法',     'greedy',              10),
    ('topic-dynamic-programming', '动态规划',     'dynamic-programming',  20),
    ('topic-binary-search',       '二分查找',     'binary-search',        30),
    ('topic-hash-table',          '哈希表',       'hash-table',           40),
    ('topic-two-pointers',        '双指针',       'two-pointers',         50),
    ('topic-stack',               '栈',           'stack',                60),
    ('topic-bfs',                 '广度优先搜索', 'bfs',                  70),
    ('topic-dfs',                 '深度优先搜索', 'dfs',                  80);
