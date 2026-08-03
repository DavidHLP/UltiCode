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
        @DisplayName("matching auth user count and app profile count are drift-free")
        void matchingCountsAreDriftFree() {
            when(authService.countActiveUsers()).thenReturn(RpcResult.success(5L, "t-system"));
            when(appPort.countUserProfiles()).thenReturn(5L);
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.countOrphanAuditLogs()).thenReturn(0L);

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getDivergenceCount()).isZero();
            assertThat(run.getOrphanCount()).isZero();
            assertThat(run.getDetail()).contains("\"drift\":false");
        }

        @Test
        @DisplayName("count mismatch increments divergence count")
        void countMismatchDetectsDrift() {
            when(authService.countActiveUsers()).thenReturn(RpcResult.success(5L, "t-system"));
            when(appPort.countUserProfiles()).thenReturn(3L);
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.countOrphanAuditLogs()).thenReturn(0L);

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getDivergenceCount()).isEqualTo(1);
            assertThat(run.getDetail()).contains("\"drift\":true");
        }
    }

    @Nested
    @DisplayName("Orphan aggregation")
    class Orphans {

        @Test
        @DisplayName("one non-zero table per owner increments orphan count once")
        void orphanTablesAreCountedPerOwner() {
            when(authService.countActiveUsers()).thenReturn(RpcResult.success(2L, "t-system"));
            when(appPort.countUserProfiles()).thenReturn(2L);
            when(authService.countAuthOrphans()).thenReturn(RpcResult.success(
                    new AuthReconciliationOrphanCounts(1, 0, 0, 0), "t-system"));
            when(appPort.countOrphans()).thenReturn(new ReconciliationOrphanCounts(
                    2, 0, 0, 0, 0, 0, 0, 0, 0));
            when(auditMapper.countOrphanAuditLogs()).thenReturn(1L);

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getOrphanCount()).isEqualTo(3);
            assertThat(run.getDetail()).contains("\"child\":\"submissions\"");
            assertThat(run.getDetail()).contains("\"child\":\"refresh_tokens\"");
            assertThat(run.getDetail()).contains("\"child\":\"audit_logs\"");
            assertThat(run.getDetail()).contains("\"orphans\":2");
        }

        @Test
        @DisplayName("failed auth RPC records zero auth orphans instead of failing")
        void failedAuthRpcDegradesToZero() {
            when(authService.countActiveUsers()).thenReturn(RpcResult.success(2L, "t-system"));
            when(appPort.countUserProfiles()).thenReturn(2L);
            when(authService.countAuthOrphans()).thenReturn(null);
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.countOrphanAuditLogs()).thenReturn(0L);

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getOrphanCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class Failures {

        @Test
        @DisplayName("exception during aggregation persists FAILED status")
        void exceptionPersistsFailedStatus() {
            when(authService.countActiveUsers()).thenThrow(new IllegalStateException("dubbo down"));

            ReconciliationRun run = reconciler.runReconciliation();

            assertThat(run.getStatus()).isEqualTo("FAILED");
            verify(runMapper).updateById(run);
        }

        @Test
        @DisplayName("run record is inserted with RUNNING status before execution")
        void runInsertedAsRunning() {
            when(authService.countActiveUsers()).thenReturn(RpcResult.success(1L, "t-system"));
            when(appPort.countUserProfiles()).thenReturn(1L);
            when(authService.countAuthOrphans())
                    .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
            when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);
            when(auditMapper.countOrphanAuditLogs()).thenReturn(0L);

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

}
