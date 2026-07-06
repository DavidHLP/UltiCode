package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.util.Map;

/**
 * Intent emitted when a user receives a reply to one of their comments.
 * Reserved by ADR-004 §2.1; the legacy comment-reply path still uses the
 * old {@code NotificationDispatchService} and will be migrated in a future
 * PR once the forum module is touched for cross-cutting reasons.
 *
 * <p>Channel projection is intentionally permissive (all three channels
 * support it) so the dispatcher does not silently drop events for callers
 * that start using this intent before the M4b channel implementation is
 * fully wired.
 */
public record CommentReplyIntent(
        String userId,
        String commentId,
        String replierUserId,
        String replierUsername,
        String preview,
        String link,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        return "comment-reply:" + userId + ":" + commentId + ":" + replierUserId;
    }

    @Override
    public NotificationPayload toPushPayload() {
        return NotificationPayload.of(
                intentId(),
                "REPLY",
                replierUsername + " replied to your comment",
                preview == null ? "" : preview,
                Map.of(
                        "commentId", commentId,
                        "replierUserId", replierUserId));
    }
}
