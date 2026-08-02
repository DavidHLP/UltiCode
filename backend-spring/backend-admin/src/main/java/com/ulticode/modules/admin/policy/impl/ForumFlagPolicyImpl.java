package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.app.api.service.ForumOwnerPort.FlagResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Default {@link ForumFlagPolicy} implementation.
 *
 * <p>P3-OWNER-001-D: routes all forum_posts write operations through
 * {@link ForumOwnerPort} rather than directly importing {@code ForumPostMapper}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumFlagPolicyImpl implements ForumFlagPolicy {

    /** Audit entity type — kept in sync with the legacy toggle methods. */
    private static final String ENTITY_FORUM_POST = "FORUM_POST";

    /** Audit action constant for flag operations. */
    private static final String ACTION_FLAG_POST = "FLAG_POST";

    /** Audit action constant for unflag operations. */
    private static final String ACTION_UNFLAG_POST = "UNFLAG_POST";

    private final ForumOwnerPort forumOwnerPort;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Override
    public void flag(String postId, String reason) {
        String normalisedReason = reason != null ? reason : "";
        LocalDateTime now = LocalDateTime.now(clock);

        FlagResult result = forumOwnerPort.flagPost(postId, reason, now);

        auditRecorder.recordForUser(
            ACTION_FLAG_POST,
            ENTITY_FORUM_POST,
            postId,
            result.authorUserId(),
            Map.of(
                "isFlagged", result.previousIsFlagged(),
                "flaggedReason", result.previousReason()
            ),
            Map.of(
                "isFlagged", true,
                "flaggedReason", normalisedReason
            )
        );

        log.info("Post flagged: {} reason: {}", postId, reason);
    }

    @Override
    public void unflag(String postId) {
        FlagResult result = forumOwnerPort.unflagPost(postId);

        auditRecorder.recordForUser(
            ACTION_UNFLAG_POST,
            ENTITY_FORUM_POST,
            postId,
            result.authorUserId(),
            Map.of(
                "isFlagged", result.previousIsFlagged(),
                "flaggedReason", result.previousReason()
            ),
            Map.of(
                "isFlagged", false,
                "flaggedReason", ""
            )
        );

        log.info("Post unflagged: {}", postId);
    }
}
