package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default {@link ForumPostFieldToggle} implementation.
 *
 * <p>The write sequence is identical for all four single-field toggles:
 * load the post, snapshot the previous field value, emit an audit entry,
 * apply the new value, persist, log. Keeping the logic in one method that
 * is parameterised by {@link FieldToggle} removes the four copy-pasted
 * bodies that used to live in {@code AdminForumServiceImpl}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumPostFieldToggleImpl implements ForumPostFieldToggle {

    /** Audit entity type constant shared with the legacy toggle methods. */
    private static final String ENTITY_FORUM_POST = "FORUM_POST";

    private final ForumPostMapper forumPostMapper;
    private final AuditHelper auditHelper;

    /**
     * Apply {@code fieldToggle} to the post identified by {@code postId}.
     *
     * @param postId      target post id
     * @param fieldToggle which field to flip and to which value
     * @throws BusinessException with {@code NOT_FOUND} when the post does not exist
     */
    @Override
    public void toggle(String postId, FieldToggle fieldToggle) {
        ForumPost post = loadOrThrow(postId);
        boolean previousValue = fieldToggle.readCurrent(post);

        // Audit-before-persist invariant: if the audit log write fails the
        // whole transaction must roll back so we never leave a write behind
        // with no audit trail.
        auditHelper.logForUser(
            fieldToggle.auditAction(),
            ENTITY_FORUM_POST,
            postId,
            post.getUserId(),
            Map.of(fieldToggle.fieldName(), previousValue),
            Map.of(fieldToggle.fieldName(), fieldToggle.newValue())
        );

        fieldToggle.applyTo(post);
        forumPostMapper.updateById(post);
        log.info("Post {}: {}", fieldToggle.logVerb(), postId);
    }

    /**
     * Load the post or throw {@link BusinessException} with {@code NOT_FOUND}.
     *
     * @param postId target post id
     * @return loaded post entity (never {@code null})
     */
    private ForumPost loadOrThrow(String postId) {
        ForumPost post = forumPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return post;
    }
}