package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.admin.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminForumServiceImpl} after the ADR-0011 Stage 2
 * extraction, the C6 forum-toggle policy collapse, and ADMIN-007.
 *
 * <p>Read tests ({@code getPosts} comment count enrichment) migrated to
 * {@link com.ulticode.modules.admin.projection.AdminForumProjectionTest}.
 * The single-field (pin / unpin / lock / unlock) and multi-field (flag /
 * unflag) toggle tests assert delegation to {@link ForumPostFieldToggle}
 * and {@link ForumFlagPolicy}; the implementation-level audit snapshot
 * logic is covered by the policy impl tests.
 *
 * <p>This test class still pins the soft-delete state machine: the remote
 * moderation write before audit recording, the {@code deletedAt} JSR-310
 * regression, the missing-post guard, and the explicit mapping of remote
 * {@code RpcResult} failures onto {@link AdminErrorCode}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminForumServiceImplTest {

    @Mock
    private AdminForumReadPort adminForumReadPort;
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
    @Mock
    private ContentModerationService contentModerationService;

    private AdminForumServiceImpl adminForumService;

    private AdminForumPostRowDTO testPost;

    @BeforeEach
    void setUp() {
        adminForumService = new AdminForumServiceImpl(
                auditService, auditRecorder, clock, currentUserProvider,
                forumPostFieldToggle, forumFlagPolicy, new AdminBulkExecutor(),
                adminForumReadPort);
        ReflectionTestUtils.setField(adminForumService, "contentModerationService", contentModerationService);

        testPost = new AdminForumPostRowDTO();
        testPost.setId("post-test-001");
        testPost.setTitle("Test Post");
        testPost.setUserId("user-001");
        testPost.setCommunityId("community-001");
        testPost.setViews(100);
        testPost.setIsPinned(false);
        testPost.setIsLocked(false);
        testPost.setIsFlagged(false);
        testPost.setIsDeleted(false);

        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("admin-001");
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
        @DisplayName("deletePost routes the owner mutation before recording the audit")
        void deletePost_mutatesBeforeAudit() {
            when(adminForumReadPort.getPost("post-1")).thenReturn(testPost);
            when(contentModerationService.apply(any(ApplyModerationCommand.class)))
                    .thenReturn(RpcResult.success(
                            new ModerationApplyResultDTO("case-1", "post-1",
                                    ApplyModerationCommand.ModerationAction.DELETE, ContentLifecycleState.DELETED),
                            "t-1"));

            adminForumService.deletePost("post-1");

            var order = inOrder(contentModerationService, auditRecorder);
            order.verify(contentModerationService).apply(any(ApplyModerationCommand.class));
            order.verify(auditRecorder).recordForUser(
                    eq(AuditVocabulary.DELETE_FORUM_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    anyMap());
        }
        @Test
        @DisplayName("deletePost records audit after routing a DELETE moderation command")
        void deletePost_logsAuditAndAppliesRemoteModeration() {
            when(adminForumReadPort.getPost("post-1")).thenReturn(testPost);
            when(contentModerationService.apply(any(ApplyModerationCommand.class)))
                    .thenReturn(RpcResult.success(
                            new ModerationApplyResultDTO("case-1", "post-1",
                                    ApplyModerationCommand.ModerationAction.DELETE, ContentLifecycleState.DELETED),
                            "t-1"));

            adminForumService.deletePost("post-1");

            verify(auditRecorder).recordForUser(
                    eq(AuditVocabulary.DELETE_FORUM_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    anyMap()
            );
            ArgumentCaptor<ApplyModerationCommand> commandCaptor =
                    ArgumentCaptor.forClass(ApplyModerationCommand.class);
            verify(contentModerationService).apply(commandCaptor.capture());
            ApplyModerationCommand command = commandCaptor.getValue();
            assertThat(command.contentId()).isEqualTo("post-1");
            assertThat(command.contentType()).isEqualTo("forum_post");
            assertThat(command.action()).isEqualTo(ApplyModerationCommand.ModerationAction.DELETE);
        }

        @Test
        @DisplayName("deletePost newValues contains isDeleted=true and LocalDateTime.now(clock) — JSR-310 regression")
        void deletePost_newValuesContainsIsDeletedAndLocalDateTime() {
            when(adminForumReadPort.getPost("post-1")).thenReturn(testPost);
            when(contentModerationService.apply(any(ApplyModerationCommand.class)))
                    .thenReturn(RpcResult.success(
                            new ModerationApplyResultDTO("case-1", "post-1",
                                    ApplyModerationCommand.ModerationAction.DELETE, ContentLifecycleState.DELETED),
                            "t-1"));

            adminForumService.deletePost("post-1");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> newValuesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(auditRecorder).recordForUser(
                    eq(AuditVocabulary.DELETE_FORUM_POST),
                    eq(AuditVocabulary.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    newValuesCaptor.capture()
            );
            Map<String, Object> newValues = newValuesCaptor.getValue();
            assertThat(newValues.get("deletedAt")).isInstanceOf(LocalDateTime.class);
            LocalDateTime deletedAt = (LocalDateTime) newValues.get("deletedAt");
            assertThat(deletedAt).isEqualTo(LocalDateTime.ofInstant(
                    Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")));
        }

        @Test
        @DisplayName("deletePost oldValues captures the pre-delete state from the read port")
        void deletePost_oldValuesFromReadPort() {
            when(adminForumReadPort.getPost("post-1")).thenReturn(testPost);
            when(contentModerationService.apply(any(ApplyModerationCommand.class)))
                    .thenReturn(RpcResult.success(
                            new ModerationApplyResultDTO("case-1", "post-1",
                                    ApplyModerationCommand.ModerationAction.DELETE, ContentLifecycleState.DELETED),
                            "t-1"));

            adminForumService.deletePost("post-1");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> oldValuesCaptor = ArgumentCaptor.forClass(Map.class);
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
        @DisplayName("deletePost throws NOT_FOUND when the post is missing and never audits or calls the provider")
        void deletePost_throwsNotFoundWhenPostMissing() {
            when(adminForumReadPort.getPost("post-1")).thenReturn(null);

            assertThatThrownBy(() -> adminForumService.deletePost("post-1"))
                    .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.NOT_FOUND.getCode());

            verify(auditRecorder, never()).recordForUser(anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
            verify(contentModerationService, never()).apply(any());
        }


        @Test
        @DisplayName("deletePost rejects an already deleted row before remote mutation or audit")
        void deletePost_rejectsAlreadyDeletedPost() {
            testPost.setIsDeleted(true);
            when(adminForumReadPort.getPost("post-1")).thenReturn(testPost);

            assertThatThrownBy(() -> adminForumService.deletePost("post-1"))
                    .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.NOT_FOUND.getCode());

            verify(contentModerationService, never()).apply(any());
            verify(auditRecorder, never()).recordForUser(
                    anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
        }
        @Test
        @DisplayName("deletePost maps remote CONTENT_NOT_FOUND onto admin NOT_FOUND")
        void deletePost_mapsRemoteNotFound() {
            when(adminForumReadPort.getPost("post-1")).thenReturn(testPost);
            when(contentModerationService.apply(any(ApplyModerationCommand.class)))
                    .thenReturn(RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, "t-1"));

            assertThatThrownBy(() -> adminForumService.deletePost("post-1"))
                    .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("flagPost delegates to flag policy")
        void flagPost_delegates() {
            adminForumService.flagPost("post-1", "Spam");
            verify(forumFlagPolicy).flag("post-1", "Spam");
        }

        @Test
        @DisplayName("unflagPost delegates to flag policy")
        void unflagPost_delegates() {
            adminForumService.unflagPost("post-1");
            verify(forumFlagPolicy).unflag("post-1");
        }
    }

    @Nested
    @DisplayName("bulk action validation")
    class BulkActionValidation {

        @Test
        @DisplayName("bulkAction unknown action returns per-item failure (not controller-level 400)")
        void bulkAction_unknownActionPerItemFailure() {
            // Pattern validation lives in @Pattern on BulkActionRequest.action; if a
            // service caller bypasses the controller (e.g. internal cron), the
            // service still must not throw and roll back the whole batch.
            var result = adminForumService.bulkAction(List.of("post-1"), "explode");

            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getSuccessful()).isZero();
            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getResults().get(0).getSuccess()).isFalse();
            assertThat(result.getResults().get(0).getError()).contains("Unknown action: explode");
        }

        @Test
        @DisplayName("bulkAction valid action delegates per-post service and reports success")
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
