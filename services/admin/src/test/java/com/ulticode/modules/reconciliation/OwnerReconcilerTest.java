package com.ulticode.modules.reconciliation;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.common.lease.FencedLease;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.lease.FencedJobLeaseService;
import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P7-RECON-AGGREGATOR-001: OwnerReconciler aggregation unit tests.
 *
 * <p>Auth facts come from the mocked Dubbo contract, App facts from the
 * mocked local read port, and the admin audit check from the mocked
 * mapper — no database involved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OwnerReconciler (RPC/read-port aggregation)")
class OwnerReconcilerTest {

    @Mock private ReconciliationRunMapper runMapper;
    @Mock private UuidGenerator uuidGenerator;
    @Mock private AppReconciliationReadPort appPort;
    @Mock private SubmissionReconciliationReadPort submissionPort;
    @Mock private NotificationReconciliationReadPort notificationPort;
    @Mock private AuditOrphanMapper auditMapper;
    @Mock private ReconciliationQueryService authService;
    @Mock private FencedJobLeaseService leaseService;
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static final FencedLease LEASE = new FencedLease(
            "admin:reconciliation", 1, "runner-a",
            Instant.parse("2026-08-31T00:00:00Z"),
            Instant.parse("2026-08-31T01:00:00Z"));

    private OwnerReconciler reconciler;

    @BeforeEach
    void setUp() {
        when(uuidGenerator.newId()).thenReturn("run-1");
        when(leaseService.tryAcquire(anyString())).thenReturn(LEASE);
        when(leaseService.renew(any(FencedLease.class))).thenReturn(true);
        when(leaseService.release(any(FencedLease.class))).thenReturn(true);
        when(runMapper.updateByIdWhileLeaseHeld(any(ReconciliationRun.class), anyString(),
                anyString(), anyLong())).thenReturn(1);
        when(submissionPort.findUserReferenceCounts("", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        when(notificationPort.findUserReferenceCounts("", null,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        reconciler = new OwnerReconciler(
                runMapper, uuidGenerator, appPort, submissionPort, notificationPort,
                auditMapper, meterRegistry, leaseService);
        ReflectionTestUtils.setField(reconciler, "authQueryService", authService);
    }

    private ReconciliationRun capturedRun() {
        ArgumentCaptor<ReconciliationRun> insertCaptor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper).insert(insertCaptor.capture());
        ArgumentCaptor<ReconciliationRun> updateCaptor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper).updateByIdWhileLeaseHeld(updateCaptor.capture(), anyString(),
                anyString(), anyLong());
        return updateCaptor.getValue();
    }
    @Test
    @DisplayName("incremental runs use the Submission owner cursor and watermark")
    void incrementalRunsUseSubmissionOwnerFacts() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 29, 0, 0);
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(submissionPort.findUserReferenceCounts("", since,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(List.of(new SubmissionUserReferenceCountDTO("ghost", 2L)));
        when(authService.existingUserIds(Set.of("ghost")))
                .thenReturn(RpcResult.success(Set.of(), "t-system"));
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
        when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        ReconciliationRun run = reconciler.runIncrementalReconciliation(since);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getDetail()).contains("\"mode\":\"INCREMENTAL\"");
        assertThat(run.getDetail()).contains("\"child\":\"submissions\"");
        verify(submissionPort).findUserReferenceCounts(
                "", since, SubmissionReconciliationReadPort.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("incremental runs use the Notification owner cursor and watermark")
    void incrementalRunsUseNotificationOwnerFacts() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 29, 0, 0);
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(submissionPort.findUserReferenceCounts("", since,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        when(notificationPort.findUserReferenceCounts("", since,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(List.of(new NotificationUserReferenceCountDTO("ghost", 3L)));
        when(authService.existingUserIds(Set.of("ghost")))
                .thenReturn(RpcResult.success(Set.of(), "t-system"));
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
        when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        ReconciliationRun run = reconciler.runIncrementalReconciliation(since);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getDetail()).contains("\"child\":\"notifications\"", "\"orphans\":3");
        verify(notificationPort).findUserReferenceCounts(
                "", since, NotificationReconciliationReadPort.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("Notification owner failures persist a failed reconciliation")
    void notificationOwnerUnavailableFailsClosed() {
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(submissionPort.findUserReferenceCounts("", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        when(notificationPort.findUserReferenceCounts("", null,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(null);
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
        when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getDetail()).contains("\"child\":\"submissions\"", "\"mode\":\"FULL\"");
        assertThat(run.getDetail()).contains("\"error\":");
    }

    @Test
    @DisplayName("Notification owner pages advance with a monotonic cursor")
    void notificationOwnerFactsAdvanceCursorAcrossPages() {
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(submissionPort.findUserReferenceCounts("", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        when(notificationPort.findUserReferenceCounts("", null,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(notificationReferences(0, NotificationReconciliationReadPort.MAX_PAGE_SIZE));
        when(notificationPort.findUserReferenceCounts("user-0499", null,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(List.of(new NotificationUserReferenceCountDTO("user-0500", 2L)));
        when(authService.existingUserIds(any()))
                .thenReturn(RpcResult.success(Set.of(), "t-system"));
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
        when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getDetail()).contains("\"child\":\"notifications\"", "\"orphans\":502");
        verify(notificationPort).findUserReferenceCounts(
                "user-0499", null, NotificationReconciliationReadPort.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("unordered Notification owner facts fail closed")
    void unorderedNotificationFactsFailClosed() {
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(submissionPort.findUserReferenceCounts("", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        when(notificationPort.findUserReferenceCounts("", null,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of(
                new NotificationUserReferenceCountDTO("user-2", 1L),
                new NotificationUserReferenceCountDTO("user-1", 1L)));
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
        when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getDetail()).contains("\"child\":\"submissions\"", "\"error\":");
    }

    @Test
    @DisplayName("a busy replica skips without creating a run record")
    void busyReplicaSkipsWithoutPersisting() {
        when(leaseService.tryAcquire(anyString())).thenReturn(null);

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("SKIPPED");
        verify(runMapper, never()).insert(any(ReconciliationRun.class));
        verify(runMapper, never()).updateById(any(ReconciliationRun.class));
        verify(runMapper, never()).updateByIdWhileLeaseHeld(any(ReconciliationRun.class),
                anyString(), anyString(), anyLong());
        verify(leaseService, never()).release(any(FencedLease.class));
        assertThat(meterRegistry.counter("reconciliation.skipped", "reason", "lease_busy").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("lock acquisition errors persist FAILED instead of being skipped")
    void lockAcquisitionErrorPersistsFailure() {
        when(leaseService.tryAcquire(anyString())).thenThrow(new IllegalStateException("db unavailable"));

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getDetail()).contains("FENCED_LEASE failed: IllegalStateException: db unavailable");
        verify(runMapper).insert(any(ReconciliationRun.class));
        verify(runMapper).updateById(run);
        assertThat(meterRegistry.counter("reconciliation.failures", "mode", "FULL").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("lock acquisition exceptions persist FAILED instead of escaping")
    void lockAcquisitionExceptionPersistsFailure() {
        when(leaseService.tryAcquire(anyString())).thenThrow(new IllegalStateException("db unavailable"));

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getDetail()).contains("FENCED_LEASE failed: IllegalStateException: db unavailable");
    }

    @Test
    @DisplayName("lost lease never publishes a stale completion")
    void lostLeaseRejectsCompletion() {
        when(leaseService.renew(any(FencedLease.class))).thenReturn(false);

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getDetail()).contains("\"mode\":\"FULL\"");
        verify(runMapper, never()).updateByIdWhileLeaseHeld(any(ReconciliationRun.class),
                anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("a superseding fence token rejects the old completion CAS")
    void staleFenceTokenRejectsCompletionCas() {
        when(runMapper.updateByIdWhileLeaseHeld(any(ReconciliationRun.class), anyString(),
                anyString(), anyLong())).thenReturn(0);

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        verify(runMapper).updateByIdWhileLeaseHeld(any(ReconciliationRun.class),
                eq("admin:reconciliation"), eq("runner-a"), eq(1L));
    }

    @Test
    @DisplayName("full scans advance the Submission owner cursor across bounded pages")
    void fullScanAdvancesSubmissionCursorAcrossPages() {
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(submissionPort.findUserReferenceCounts("", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(references(0, SubmissionReconciliationReadPort.MAX_PAGE_SIZE));
        when(submissionPort.findUserReferenceCounts("user-0499", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(List.of(new SubmissionUserReferenceCountDTO("user-0500", 2L)));
        when(authService.existingUserIds(any()))
                .thenReturn(RpcResult.success(Set.of(), "t-system"));
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
        when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        ReconciliationRun run = reconciler.runReconciliation();

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getDetail()).contains("\"orphans\":502");
        verify(submissionPort).findUserReferenceCounts(
                "user-0499", null, SubmissionReconciliationReadPort.MAX_PAGE_SIZE);
    }
    @Nested
    @DisplayName("Checksum aggregation")
    class Checksum {

        @Test
        @DisplayName("closed profile dual-write window produces no count reconciliation")
        void closedProfileDualWriteWindowProducesNoCountReconciliation() {
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class))).thenReturn(List.of());

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getDivergenceCount()).isZero();
            assertThat(run.getOrphanCount()).isZero();
            assertThat(run.getDetail()).contains("\"reconciliation\":[]");
        }

        @Test
        @DisplayName("profile count mismatch is not reconciled after the split is complete")
        void profileCountMismatchIsNotReconciledAfterSplit() {
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class))).thenReturn(List.of());

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getDivergenceCount()).isZero();
            assertThat(run.getDetail()).contains("\"reconciliation\":[]");
        }
    }

    @Nested
    @DisplayName("Orphan aggregation")
    class Orphans {

        @Test
        @DisplayName("one non-zero table per owner increments orphan count once")
        void orphanTablesAreCountedPerOwner() {
            when(authService.countAuthOrphans()).thenReturn(RpcResult.success(
                    new AuthReconciliationOrphanCounts(1, 0, 0, 0), "t-system"));
            when(appPort.countOrphans()).thenReturn(new ReconciliationOrphanCounts(
                    0, 0, 0, 0, 0, 0, 0, 0, 0));
            when(submissionPort.findUserReferenceCounts("", null,
                    SubmissionReconciliationReadPort.MAX_PAGE_SIZE))
                    .thenReturn(List.of(new SubmissionUserReferenceCountDTO("ghost", 2L)));
            when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                    .thenReturn(List.of(reference("ghost", 1)));
            when(authService.existingUserIds(Set.of("ghost")))
                    .thenReturn(RpcResult.success(Set.of(), "t-system"));

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getOrphanCount()).isEqualTo(3);
            assertThat(run.getDetail()).contains("\"child\":\"submissions\"");
            assertThat(run.getDetail()).contains("\"child\":\"refresh_tokens\"");
            assertThat(run.getDetail()).contains("\"child\":\"audit_logs\"");
            assertThat(run.getDetail()).contains("\"orphans\":2");
        }
        @Test
        @DisplayName("blank and empty performer IDs are counted as audit orphans")
        void blankPerformerIdsAreOrphans() {
            when(authService.countAuthOrphans()).thenReturn(RpcResult.success(
                    AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                    .thenReturn(List.of(reference("", 1), reference("  ", 1), reference("ghost", 1)));
            when(authService.existingUserIds(Set.of("", "  ", "ghost")))
                    .thenReturn(RpcResult.success(Set.of(), "t-system"));

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getOrphanCount()).isEqualTo(1);
            assertThat(run.getDetail()).contains("\"orphans\":3");
        }

        @Test
        @DisplayName("failed auth RPC fails the reconciliation run closed")
        void failedAuthRpcFailsClosed() {
            when(authService.countAuthOrphans()).thenReturn(null);

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getOrphanCount()).isZero();
            assertThat(run.getDetail()).contains("\"mode\":\"FULL\"", "\"error\":");
            assertThat(meterRegistry.counter("reconciliation.failures", "mode", "FULL").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("unordered Submission owner facts fail closed")
        void unorderedSubmissionFactsFailClosed() {
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(submissionPort.findUserReferenceCounts("", null,
                    SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of(
                    new SubmissionUserReferenceCountDTO("user-2", 1L),
                    new SubmissionUserReferenceCountDTO("user-1", 1L)));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                    .thenReturn(List.of());

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getDetail()).contains("\"mode\":\"FULL\"", "\"error\":");
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class Failures {

        @Test
        @DisplayName("exception during aggregation persists FAILED status")
        void exceptionPersistsFailedStatus() {
            when(authService.countAuthOrphans()).thenThrow(new IllegalStateException("dubbo down"));

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getDetail()).contains("IllegalStateException: dubbo down");
            verify(runMapper).updateByIdWhileLeaseHeld(any(ReconciliationRun.class),
                    anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("run record is inserted with RUNNING status before execution")
        void runInsertedAsRunning() {
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.auditPerformerIds(any(Integer.class), any(Integer.class)))
                    .thenReturn(List.of());

            AtomicReference<String> statusAtInsert = new AtomicReference<>();
            when(runMapper.insert(any(ReconciliationRun.class))).thenAnswer(invocation -> {
                statusAtInsert.set(((ReconciliationRun) invocation.getArgument(0)).getStatus());
                return 1;
            });
            reconciler.runReconciliation();

            assertThat(statusAtInsert.get()).isEqualTo("RUNNING");
            assertThat(capturedRun().getOwner()).isEqualTo("ALL");
        }
    }

    private static AuditReferenceCount reference(String performerId, long rowCount) {
        AuditReferenceCount reference = new AuditReferenceCount();
        reference.setPerformerId(performerId);
        reference.setRowCount(rowCount);
        return reference;
    }

    private static List<SubmissionUserReferenceCountDTO> references(int start, int count) {
        return IntStream.range(start, start + count)
                .mapToObj(index -> new SubmissionUserReferenceCountDTO(
                        "user-%04d".formatted(index), 1L))
                .toList();
    }

    private static List<NotificationUserReferenceCountDTO> notificationReferences(int start, int count) {
        return IntStream.range(start, start + count)
                .mapToObj(index -> new NotificationUserReferenceCountDTO(
                        "user-%04d".formatted(index), 1L))
                .toList();
    }

}
