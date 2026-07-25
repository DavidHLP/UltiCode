package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.util.Map;

/**
 * Intent emitted when a user receives a reply to one of their comments.
 * Reserved by ADR-004 §2.1 — no producer constructs it yet; the forum
 * comment-reply path will dispatch this typed intent once that module is
 * touched. Channel projection is implemented (all three channels support
 * it) so the dispatcher does not silently drop events for the first caller.
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
    public String wireType() {
        return "REPLY";
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
