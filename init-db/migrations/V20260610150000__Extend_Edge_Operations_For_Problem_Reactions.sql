-- V20260610150000__Extend_Edge_Operations_For_Problem_Reactions.sql
-- D-10 (problem-api report): extend edge_operations.operation_type to support problem LIKE/DISLIKE/FAVORITE
-- so frontend can show viewer's own reaction (interactions.viewer.reaction) per user.
--
-- Backward compatible: only ADDS new enum values, existing rows unchanged.
-- The unique key (operator_id, operation_type, target_type, target_id) already supports these.

ALTER TABLE edge_operations
    MODIFY COLUMN operation_type
    ENUM('VOTE_UP','VOTE_DOWN','ANALYZE','VIEW','LIKE','DISLIKE','FAVORITE') NOT NULL;
