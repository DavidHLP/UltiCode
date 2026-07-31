package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.forum.port.ForumOwnerPort;
import com.ulticode.modules.forum.port.ForumOwnerPort.ToggleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default {@link ForumPostFieldToggle} implementation.
 *
 * <p>P3-OWNER-001-D: routes all forum_posts toggle operations through
 * {@link ForumOwnerPort} rather than directly importing {@code ForumPostMapper}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumPostFieldToggleImpl implements ForumPostFieldToggle {

    /** Audit entity type constant shared with the legacy toggle methods. */
    private static final String ENTITY_FORUM_POST = "FORUM_POST";

    private final ForumOwnerPort forumOwnerPort;
    private final AuditRecorder auditRecorder;

    /**
     * Apply {@code fieldToggle} to the post identified by {@code postId}.
     *
     * @param postId      target post id
     * @param fieldToggle which field to flip and to which value
     * @throws BusinessException with {@code NOT_FOUND} when the post does not exist
     */
    @Override
    public void toggle(String postId, FieldToggle fieldToggle) {
        ToggleResult result;
        if ("isPinned".equals(fieldToggle.fieldName())) {
            result = forumOwnerPort.setPinned(postId, fieldToggle.newValue());
        } else if ("isLocked".equals(fieldToggle.fieldName())) {
            result = forumOwnerPort.setLocked(postId, fieldToggle.newValue());
        } else {
            throw new IllegalArgumentException("Unsupported toggle field: " + fieldToggle.fieldName());
        }

        auditRecorder.recordForUser(
            fieldToggle.auditAction(),
            ENTITY_FORUM_POST,
            postId,
            result.authorUserId(),
            Map.of(fieldToggle.fieldName(), result.previousValue()),
            Map.of(fieldToggle.fieldName(), fieldToggle.newValue())
        );

        log.info("Post {}: {}", fieldToggle.logVerb(), postId);
    }
}
