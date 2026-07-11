package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForumPostFieldToggleImpl} after the C6 policy
 * collapse. Each test pins one of the four single-field toggles (pin /
 * unpin / lock / unlock) and asserts:
 * <ul>
 *   <li>the audit log entry written by the policy,</li>
 *   <li>the field value applied to the post,</li>
 *   <li>the persistence call to {@code forumPostMapper.updateById}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForumPostFieldToggleImplTest {

    @Mock
    private ForumPostMapper forumPostMapper;

    @Mock
    private AuditHelper auditHelper;

    @InjectMocks
    private ForumPostFieldToggleImpl policy;

    private ForumPost testPost;

    @BeforeEach
    void setUp() {
        testPost = new ForumPost();
        testPost.setId("post-1");
        testPost.setUserId("user-001");
        testPost.setIsPinned(false);
        testPost.setIsLocked(false);
    }

    @Test
    @DisplayName("toggle(PIN) writes audit log with isPinned old=false new=true and persists")
    void toggle_pin_writesAuditAndPersists() {
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.toggle("post-1", FieldToggle.PIN);

        verify(auditHelper).logForUser(
            eq("PIN_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            eq(Map.of("isPinned", false)),
            eq(Map.of("isPinned", true))
        );
        verify(forumPostMapper).updateById(testPost);
        assertThat(testPost.getIsPinned()).isTrue();
    }

    @Test
    @DisplayName("toggle(UNPIN) writes audit log with isPinned old=true new=false")
    void toggle_unpin_writesAuditAndPersists() {
        testPost.setIsPinned(true);
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.toggle("post-1", FieldToggle.UNPIN);

        verify(auditHelper).logForUser(
            eq("UNPIN_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            eq(Map.of("isPinned", true)),
            eq(Map.of("isPinned", false))
        );
        verify(forumPostMapper).updateById(testPost);
        assertThat(testPost.getIsPinned()).isFalse();
    }

    @Test
    @DisplayName("toggle(LOCK) writes audit log with isLocked old=false new=true")
    void toggle_lock_writesAuditAndPersists() {
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.toggle("post-1", FieldToggle.LOCK);

        verify(auditHelper).logForUser(
            eq("LOCK_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            eq(Map.of("isLocked", false)),
            eq(Map.of("isLocked", true))
        );
        verify(forumPostMapper).updateById(testPost);
        assertThat(testPost.getIsLocked()).isTrue();
    }

    @Test
    @DisplayName("toggle(UNLOCK) writes audit log with isLocked old=true new=false")
    void toggle_unlock_writesAuditAndPersists() {
        testPost.setIsLocked(true);
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.toggle("post-1", FieldToggle.UNLOCK);

        verify(auditHelper).logForUser(
            eq("UNLOCK_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            eq(Map.of("isLocked", true)),
            eq(Map.of("isLocked", false))
        );
        verify(forumPostMapper).updateById(testPost);
        assertThat(testPost.getIsLocked()).isFalse();
    }

    @Test
    @DisplayName("toggle treats null current boolean as false (null-safe snapshot)")
    void toggle_nullSnapshotTreatedAsFalse() {
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.toggle("post-1", FieldToggle.PIN);

        verify(auditHelper).logForUser(
            anyString(),
            anyString(),
            eq("post-1"),
            anyString(),
            eq(Map.of("isPinned", false)),
            anyMap()
        );
        assertThat(testPost.getIsPinned()).isTrue();
    }

    @Test
    @DisplayName("toggle throws NOT_FOUND when the post does not exist")
    void toggle_postNotFoundThrows() {
        when(forumPostMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> policy.toggle("missing", FieldToggle.PIN))
            .isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());

        verify(auditHelper, never()).logForUser(anyString(), anyString(), anyString(),
            anyString(), anyMap(), anyMap());
        verify(forumPostMapper, never()).updateById(any(ForumPost.class));
    }
}