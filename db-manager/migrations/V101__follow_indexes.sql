SET FOREIGN_KEY_CHECKS = 0;

-- Composite index for: SELECT * FROM user_follows WHERE following_id = ? ORDER BY created_at DESC
CREATE INDEX idx_user_follows_following_created ON user_follows(following_id, created_at DESC);

-- Composite index for: SELECT * FROM user_follows WHERE follower_id = ? ORDER BY created_at DESC
CREATE INDEX idx_user_follows_follower_created ON user_follows(follower_id, created_at DESC);

SET FOREIGN_KEY_CHECKS = 1;
