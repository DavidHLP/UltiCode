-- Fix Forum User References
-- forum_users only has: id, username, avatar, karma
-- forum_comments uses 'author_id' not 'user_id'
-- No user_id column in forum_users to fix

-- Step 1: Update forum_comments - fix author_id references
UPDATE `forum_comments` SET `author_id` = 'admin-001' WHERE `author_id` = 'u-admin-001';

-- Step 2: Update forum_community_members - fix user_id references
UPDATE `forum_community_members` SET `user_id` = 'admin-001' WHERE `user_id` = 'u-admin-001';

-- Step 3: Update forum_posts - fix user_id references
UPDATE `forum_posts` SET `user_id` = 'admin-001' WHERE `user_id` = 'u-admin-001';
