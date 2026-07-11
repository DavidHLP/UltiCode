package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForumFlagPolicyImpl} after the C6 policy collapse.
 * Flag/unflag stay in a separate policy because they touch three fields,
 * carry a reason parameter, and depend on {@link Clock}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForumFlagPolicyImplTest {

    @Mock
    private ForumPostMapper forumPostMapper;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private Clock clock;

    @InjectMocks
    private ForumFlagPolicyImpl policy;

    private ForumPost testPost;

    @BeforeEach
    void setUp() {
        testPost = new ForumPost();
        testPost.setId("post-1");
        testPost.setUserId("user-001");
        testPost.setIsFlagged(false);
        testPost.setFlaggedReason(null);
        testPost.setFlaggedAt(null);

        // Freeze clock so LocalDateTime.now(clock) is deterministic.
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("flag writes audit and stamps flaggedAt via injected Clock")
    void flag_writesAuditAndStampsClock() {
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.flag("post-1", "Spam content for testing");

        LocalDateTime expectedFlaggedAt = LocalDateTime.ofInstant(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> newValuesCaptor =
            (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(auditHelper).logForUser(
            eq("FLAG_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            anyMap(),
            newValuesCaptor.capture()
        );
        Map<String, Object> newValues = newValuesCaptor.getValue();
        assertThat(newValues)
            .containsEntry("isFlagged", true)
            .containsEntry("flaggedReason", "Spam content for testing");
        verify(forumPostMapper).updateById(testPost);

        assertThat(testPost.getIsFlagged()).isTrue();
        assertThat(testPost.getFlaggedReason()).isEqualTo("Spam content for testing");
        assertThat(testPost.getFlaggedAt()).isEqualTo(expectedFlaggedAt);
    }

    @Test
    @DisplayName("flag with null reason stores empty string in audit newValues")
    void flag_nullReasonStoredAsEmpty() {
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.flag("post-1", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> newValuesCaptor =
            (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(auditHelper).logForUser(
            eq("FLAG_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            anyMap(),
            newValuesCaptor.capture()
        );
        Map<String, Object> newValues = newValuesCaptor.getValue();
        assertThat(newValues).containsEntry("flaggedReason", "");
    }

    @Test
    @DisplayName("unflag clears isFlagged/flaggedReason/flaggedAt and writes audit")
    void unflag_clearsFieldsAndWritesAudit() {
        testPost.setIsFlagged(true);
        testPost.setFlaggedReason("old reason");
        testPost.setFlaggedAt(LocalDateTime.of(2025, 12, 31, 0, 0));
        when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

        policy.unflag("post-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> newValuesCaptor =
            (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(auditHelper).logForUser(
            eq("UNFLAG_POST"),
            eq("FORUM_POST"),
            eq("post-1"),
            eq("user-001"),
            anyMap(),
            newValuesCaptor.capture()
        );
        Map<String, Object> newValues = newValuesCaptor.getValue();
        assertThat(newValues)
            .containsEntry("isFlagged", false)
            .containsEntry("flaggedReason", "");
        verify(forumPostMapper).updateById(testPost);

        assertThat(testPost.getIsFlagged()).isFalse();
        assertThat(testPost.getFlaggedReason()).isNull();
        assertThat(testPost.getFlaggedAt()).isNull();
    }

    @Test
    @DisplayName("flag throws NOT_FOUND when the post does not exist")
    void flag_postNotFoundThrows() {
        when(forumPostMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> policy.flag("missing", "reason"))
            .isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());

        verify(auditHelper, never()).logForUser(anyString(), anyString(), anyString(),
            anyString(), anyMap(), anyMap());
        verify(forumPostMapper, never()).updateById(any(ForumPost.class));
    }

    @Test
    @DisplayName("unflag throws NOT_FOUND when the post does not exist")
    void unflag_postNotFoundThrows() {
        when(forumPostMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> policy.unflag("missing"))
            .isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());

        verify(forumPostMapper, never()).updateById(any(ForumPost.class));
    }
}