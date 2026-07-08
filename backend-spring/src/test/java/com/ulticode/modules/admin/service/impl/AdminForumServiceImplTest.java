package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link AdminForumServiceImpl} after the ADR-0011 Stage 2
 * extraction.
 *
 * <p>Read tests ({@code getPosts} comment count enrichment) migrated to
 * {@link com.ulticode.modules.admin.projection.AdminForumProjectionTest}.
 * This test class pins the write state machine only: pin / unpin / lock /
 * unlock / soft-delete / flag / unflag audit logging, concurrent-delete
 * guard, and bulk-action validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminForumServiceImplTest {

    @Mock
    private ForumPostMapper forumPostMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditHelper auditHelper;
    @Mock
    private Clock clock;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AdminForumServiceImpl adminForumService;

    private ForumPost testPost;

    @BeforeEach
    void setUp() {
        testPost = new ForumPost();
        testPost.setId("post-test-001");
        testPost.setTitle("Test Post");
        testPost.setUserId("user-001");
        testPost.setCommunityId("community-001");
        testPost.setViews(100);
        testPost.setIsPinned(false);
        testPost.setIsLocked(false);
        testPost.setIsFlagged(false);
        testPost.setIsDeleted(false);

        // Freeze clock so LocalDateTime.now(clock) is deterministic in tests
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    @Nested
    @DisplayName("audit logging")
    class AuditLogging {

        @Test
        @DisplayName("pinPost logs audit with old and new isPinned values")
        void pinPost_logsAudit() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.pinPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.PIN_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isPinned", false)),
                    eq(Map.of("isPinned", true))
            );
            verify(forumPostMapper).updateById(testPost);
        }

        @Test
        @DisplayName("unpinPost logs audit with old and new isPinned values")
        void unpinPost_logsAudit() {
            testPost.setIsPinned(true);
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.unpinPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.UNPIN_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isPinned", true)),
                    eq(Map.of("isPinned", false))
            );
        }

        @Test
        @DisplayName("lockPost logs audit with old and new isLocked values")
        void lockPost_logsAudit() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.lockPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.LOCK_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isLocked", false)),
                    eq(Map.of("isLocked", true))
            );
        }

        @Test
        @DisplayName("unlockPost logs audit with old and new isLocked values")
        void unlockPost_logsAudit() {
            testPost.setIsLocked(true);
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.unlockPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.UNLOCK_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isLocked", true)),
                    eq(Map.of("isLocked", false))
            );
        }

        @Test
        @DisplayName("deletePost logs audit and uses softDelete mapper (not updateById) for @TableLogic column")
        void deletePost_logsAuditAndUsesSoftDelete() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);
            when(forumPostMapper.softDelete(eq("post-1"), anyString())).thenReturn(1);

            adminForumService.deletePost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.DELETE_FORUM_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    anyMap()
            );
            // Must NOT call updateById — is_deleted carries @TableLogic, so
            // MyBatis-Plus silently drops the field. Use the dedicated mapper
            // method that issues UPDATE ... SET is_deleted=1, deleted_at=NOW().
            verify(forumPostMapper).softDelete(eq("post-1"), anyString());
            verify(forumPostMapper, never()).updateById(any(ForumPost.class));
        }

        @Test
        @DisplayName("deletePost newValues contains isDeleted=true and LocalDateTime.now(clock) — JSR-310 regression")
        void deletePost_newValuesContainsIsDeletedAndLocalDateTime() {
            // Regression for docs/forum-api-curl-test-report-2026-06-08.md §3:
            // JacksonTypeHandler needs JavaTimeModule to serialize LocalDateTime inside
            // audit_logs.new_values. Before the fix the call chain threw an
            // InvalidDefinitionException and rolled back the soft-delete.
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);
            when(forumPostMapper.softDelete(anyString(), anyString())).thenReturn(1);

            // The service stamps deletedAt from LocalDateTime.now(clock), where
            // the clock is stubbed to 2026-01-01T00:00:00Z. The test asserts
            // the exact value the clock produces (not a wall-clock range),
            // matching the post-Clock-migration contract.
            LocalDateTime expectedDeletedAt = LocalDateTime.ofInstant(
                    Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
            adminForumService.deletePost("post-1");

            org.mockito.ArgumentCaptor<Map<String, Object>> newValuesCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.DELETE_FORUM_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    newValuesCaptor.capture()
            );

            Map<String, Object> newValues = newValuesCaptor.getValue();
            assertThat(newValues).containsEntry("isDeleted", true);
            assertThat(newValues).containsKey("deletedAt");
            assertThat(newValues.get("deletedAt")).isInstanceOf(LocalDateTime.class);
            LocalDateTime deletedAt = (LocalDateTime) newValues.get("deletedAt");
            assertThat(deletedAt).isEqualTo(expectedDeletedAt);
        }

        @Test
        @DisplayName("deletePost oldValues captures previous isDeleted state (false before soft delete)")
        void deletePost_oldValuesCapturesPreviousState() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);
            when(forumPostMapper.softDelete(anyString(), anyString())).thenReturn(1);

            adminForumService.deletePost("post-1");

            org.mockito.ArgumentCaptor<Map<String, Object>> oldValuesCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.DELETE_FORUM_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    oldValuesCaptor.capture(),
                    anyMap()
            );
            assertThat(oldValuesCaptor.getValue()).containsEntry("isDeleted", false);
        }

        @Test
        @DisplayName("deletePost throws NOT_FOUND when softDelete affects 0 rows (concurrent delete)")
        void deletePost_concurrentDeleteThrowsNotFound() {
            // Regression: between the initial selectById and the softDelete call,
            // the row may have been modified/deleted by another transaction
            // (e.g. concurrent admin, or row no longer matches the cached state).
            // The service must detect the 0-row count and signal failure rather
            // than silently leaving the audit log dangling against a non-delete.
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);
            when(forumPostMapper.softDelete(anyString(), anyString())).thenReturn(0);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> adminForumService.deletePost("post-1"))
                    .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                    .extracting("code").isEqualTo(com.ulticode.common.exception.ErrorCode.NOT_FOUND.getCode());

            verify(forumPostMapper).softDelete(eq("post-1"), anyString());
        }

        @Test
        @DisplayName("flagPost newValues contains isFlagged=true and flaggedReason — audit UI requirement")
        void flagPost_newValuesContainsReason() {
            // Regression for docs/forum-api-curl-test-report-2026-06-08.md §4 #4:
            // flag/unflag must write the reason into audit so the UI can render before/after.
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.flagPost("post-1", "Spam content for testing");

            org.mockito.ArgumentCaptor<Map<String, Object>> newValuesCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.FLAG_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    newValuesCaptor.capture()
            );
            assertThat(newValuesCaptor.getValue())
                    .containsEntry("isFlagged", true)
                    .containsEntry("flaggedReason", "Spam content for testing");
        }

        @Test
        @DisplayName("unflagPost newValues contains isFlagged=false and clears flaggedReason")
        void unflagPost_newValuesClearsReason() {
            testPost.setIsFlagged(true);
            testPost.setFlaggedReason("old reason");
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.unflagPost("post-1");

            org.mockito.ArgumentCaptor<Map<String, Object>> newValuesCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.UNFLAG_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    newValuesCaptor.capture()
            );
            assertThat(newValuesCaptor.getValue())
                    .containsEntry("isFlagged", false)
                    .containsEntry("flaggedReason", "");
        }
    }

    @Nested
    @DisplayName("bulk action validation")
    class BulkActionValidation {

        @Test
        @DisplayName("bulkAction with unknown action returns per-item failure (not controller-level 400)")
        void bulkAction_unknownActionPerItemFailure() {
            // Pattern validation lives in @Pattern on BulkActionRequest.action; if a
            // service caller bypasses the controller (e.g. internal cron), the
            // service still must not throw and roll back the whole batch.
            // Note: unknown action short-circuits the switch in deletePost/pinPost
            // dispatch — it never calls forumPostMapper.selectById, hence no stubbing.
            var result = adminForumService.bulkAction(List.of("post-1"), "explode");

            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getSuccessful()).isZero();
            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getResults().get(0).getSuccess()).isFalse();
            assertThat(result.getResults().get(0).getError()).contains("Unknown action: explode");
        }

        @Test
        @DisplayName("bulkAction with valid action delegates to per-post service and reports success")
        void bulkAction_validActionDelegates() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            var result = adminForumService.bulkAction(List.of("post-1"), "pin");

            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getSuccessful()).isEqualTo(1);
            assertThat(result.getFailed()).isZero();
            assertThat(result.getResults().get(0).getSuccess()).isTrue();
            verify(auditHelper).logForUser(
                    eq(AuditVocabulary.PIN_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    anyMap()
            );
        }
    }
}
