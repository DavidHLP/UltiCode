-- NOTIFY-002: the typed NotificationIntent wire values must be accepted by
-- notifications.type before the durable in-app channel can persist them.
-- This is append-only; existing notification values remain valid.
ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'COMMENT', 'REPLY', 'MENTION', 'UPVOTE', 'FOLLOW', 'SYSTEM',
        'SUBMISSION', 'CONTEST', 'CONTEST_REMINDER', 'ACHIEVEMENT'
    ) NOT NULL;
