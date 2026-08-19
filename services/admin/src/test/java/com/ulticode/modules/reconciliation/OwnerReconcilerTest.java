package com.ulticode.modules.reconciliation;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.UuidGenerator;
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private AuditOrphanMapper auditMapper;
    @Mock private ReconciliationQueryService authService;

    private OwnerReconciler reconciler;

    @BeforeEach
    void setUp() {
        when(uuidGenerator.newId()).thenReturn("run-1");
        reconciler = new OwnerReconciler(runMapper, uuidGenerator, appPort, auditMapper);
        ReflectionTestUtils.setField(reconciler, "authQueryService", authService);
    }

    private ReconciliationRun capturedRun() {
        ArgumentCaptor<ReconciliationRun> insertCaptor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper).insert(insertCaptor.capture());
        ArgumentCaptor<ReconciliationRun> updateCaptor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper).updateById(updateCaptor.capture());
        return updateCaptor.getValue();
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
                    2, 0, 0, 0, 0, 0, 0, 0, 0));
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
            verify(runMapper).updateById(run);
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

}
