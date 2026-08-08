package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminForumServiceImpl} after the ADR-0011 Stage 2
 * extraction and the C6 forum-toggle policy collapse.
 *
 * <p>Read tests ({@code getPosts} comment count enrichment) migrated to
 * {@link com.ulticode.modules.admin.projection.AdminForumProjectionTest}.
 * The single-field (pin / unpin / lock / unlock) and multi-field (flag /
 * unflag) toggle tests now assert delegation to {@link ForumPostFieldToggle}
 * and {@link ForumFlagPolicy}; the implementation-level audit snapshot logic
 * is covered by {@link com.ulticode.modules.admin.policy.impl.ForumPostFieldToggleImplTest}
 * and {@link com.ulticode.modules.admin.policy.impl.ForumFlagPolicyImplTest}.
 *
 * <p>This test class still pins the soft-delete state machine, the
 * concurrent-delete guard, and bulk-action validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminForumServiceImplTest {

    @Mock
    private ForumPostMapper forumPostMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private Clock clock;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ForumPostFieldToggle forumPostFieldToggle;
    @Mock
    private ForumFlagPolicy forumFlagPolicy;

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

        // Manual construction matches AdminCommentServiceImplTest and
        // AdminSolutionServiceImplTest; the executor is a thin pure module
        // with no external dependencies, so a real instance is the right test
        // double (not @Spy, not @Mock).
        adminForumService = new AdminForumServiceImpl(
                forumPostMapper,
                auditService,
                auditRecorder,
                clock,
                currentUserProvider,
                forumPostFieldToggle,
                forumFlagPolicy,
                new com.ulticode.modules.admin.bulk.AdminBulkExecutor());
    }

    @Nested
    @DisplayName("audit logging")
    class AuditLogging {

        @Test
        @DisplayName("pinPost delegates to fieldToggle policy with PIN enum")
        void pinPost_logsAudit() {
            adminForumService.pinPost("post-1");

            verify(forumPostFieldToggle).toggle("post-1", FieldToggle.PIN);
            verify(forumPostFieldToggle).toggle(eq("post-1"), eq(FieldToggle.PIN));
        }

        @Test
        @DisplayName("unpinPost delegates to fieldToggle policy with UNPIN enum")
        void unpinPost_logsAudit() {
            adminForumService.unpinPost("post-1");

            verify(forumPostFieldToggle).toggle("post-1", FieldToggle.UNPIN);
        }

        @Test
        @DisplayName("lockPost delegates to fieldToggle policy with LOCK enum")
        void lockPost_logsAudit() {
            adminForumService.lockPost("post-1");

            verify(forumPostFieldToggle).toggle("post-1", FieldToggle.LOCK);
        }

        @Test
        @DisplayName("unlockPost delegates to fieldToggle policy with UNLOCK enum")
        void unlockPost_logsAudit() {
            adminForumService.unlockPost("post-1");

            verify(forumPostFieldToggle).toggle("post-1", FieldToggle.UNLOCK);
        }

        @Test
        @DisplayName("deletePost logs audit and uses softDelete mapper (not updateById) for @TableLogic column")
        void deletePost_logsAuditAndUsesSoftDelete() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);
            when(forumPostMapper.softDelete(eq("post-1"), anyString())).thenReturn(1);

            adminForumService.deletePost("post-1");

            verify(auditRecorder).recordForUser(
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
            verify(auditRecorder).recordForUser(
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
            verify(auditRecorder).recordForUser(
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
                    .extracting("code").isEqualTo(com.ulticode.admin.error.AdminErrorCode.NOT_FOUND.getCode());

            verify(forumPostMapper).softDelete(eq("post-1"), anyString());
        }

        @Test
        @DisplayName("flagPost delegates to flagPolicy.flag with the supplied reason")
        void flagPost_newValuesContainsReason() {
            adminForumService.flagPost("post-1", "Spam content for testing");

            verify(forumFlagPolicy).flag("post-1", "Spam content for testing");
        }

        @Test
        @DisplayName("unflagPost delegates to flagPolicy.unflag")
        void unflagPost_newValuesClearsReason() {
            adminForumService.unflagPost("post-1");

            verify(forumFlagPolicy).unflag("post-1");
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
            var result = adminForumService.bulkAction(List.of("post-1"), "pin");

            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getSuccessful()).isEqualTo(1);
            assertThat(result.getFailed()).isZero();
            assertThat(result.getResults().get(0).getSuccess()).isTrue();
            verify(forumPostFieldToggle).toggle("post-1", FieldToggle.PIN);
        }
    }
}
