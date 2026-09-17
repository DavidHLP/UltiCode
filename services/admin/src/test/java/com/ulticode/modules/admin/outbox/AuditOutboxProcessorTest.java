package com.ulticode.modules.admin.outbox;
import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;

import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditOutboxProcessorTest {

    @Mock
    private AuditOutboxMapper auditOutboxMapper;

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AuditOutboxProcessor(auditOutboxMapper, auditLogMapper);
    }

    @Test
    @DisplayName("processRecordInNewTx inserts AuditLog and reports the recorded outcome")
    void processRecordInNewTx_insertsLogAndMarksProcessed() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("rec-100");
        record.setClaimOwner("owner-1");
        record.setPerformerId("perf-1");
        record.setUserId("user-1");
        record.setAction("TEST_ACTION");
        record.setEntityType("TEST_ENTITY");
        record.setEntityId("ent-1");
        record.setOldValues(Map.of("k1", "v1"));
        record.setNewValues(Map.of("k2", "v2"));
        record.setIpAddress("127.0.0.1");
        record.setUserAgent("Mozilla");
        when(auditOutboxMapper.markProcessed(eq("rec-100"), eq("owner-1"))).thenReturn(1);
        when(auditLogMapper.insert(any(AuditLog.class))).thenReturn(1);

        assertThat(processor.processRecordInNewTx(record))
                .isEqualTo(AuditOutboxOutcome.RECORDED);

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(logCaptor.capture());

        AuditLog auditLog = logCaptor.getValue();
        assertThat(auditLog.getPerformerId()).isEqualTo("perf-1");
        assertThat(auditLog.getAction()).isEqualTo("TEST_ACTION");
        assertThat(auditLog.getEntityId()).isEqualTo("ent-1");

        verify(auditOutboxMapper).markProcessed("rec-100", "owner-1");
        verify(auditLogMapper).insert(logCaptor.getValue());
    }

    @Test
    @DisplayName("markFailedInNewTx forwards failure metadata and reports a retryable outcome")
    void markFailedInNewTx_reportsRetryableFailure() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("rec-200");
        record.setAttempts(0);
        when(auditOutboxMapper.markFailedWithRetry(
                eq("rec-200"), eq("owner-2"), eq("constraint failure"), eq(5)))
                .thenReturn(1);

        assertThat(processor.markFailedInNewTx(record, "owner-2", "constraint failure", 5))
                .isEqualTo(AuditOutboxOutcome.FAILED_RETRYABLE);
        verify(auditOutboxMapper).markFailedWithRetry(
                "rec-200", "owner-2", "constraint failure", 5);
    }

    @Test
    @DisplayName("markFailedInNewTx reports a lost claim without throwing")
    void markFailedInNewTx_reportsLostClaim() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("rec-lost");
        when(auditOutboxMapper.markFailedWithRetry(
                eq("rec-lost"), eq("owner-lost"), eq("transient failure"), eq(5)))
                .thenReturn(0);

        assertThat(processor.markFailedInNewTx(record, "owner-lost", "transient failure", 5))
                .isEqualTo(AuditOutboxOutcome.LOST_CLAIM);
    }

    @Test
    @DisplayName("failure outcomes progress from retryable to terminal at the ceiling")
    void markFailedInNewTx_progressesRetryableToTerminal() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("rec-progress");
        when(auditOutboxMapper.markFailedWithRetry(
                eq("rec-progress"), eq("owner-progress"), eq("transient failure"), eq(5)))
                .thenReturn(1, 1, 1, 1, 1);

        List<AuditOutboxOutcome> outcomes = new ArrayList<>();
        for (int attempts = 0; attempts < 5; attempts++) {
            record.setAttempts(attempts);
            outcomes.add(processor.markFailedInNewTx(
                    record, "owner-progress", "transient failure", 5));
        }

        assertThat(outcomes).containsExactly(
                AuditOutboxOutcome.FAILED_RETRYABLE,
                AuditOutboxOutcome.FAILED_RETRYABLE,
                AuditOutboxOutcome.FAILED_RETRYABLE,
                AuditOutboxOutcome.FAILED_RETRYABLE,
                AuditOutboxOutcome.FAILED_TERMINAL);
        verify(auditOutboxMapper, times(5)).markFailedWithRetry(
                "rec-progress", "owner-progress", "transient failure", 5);
    }

    @Test
    @DisplayName("mapper SQL distinguishes due retryable FAILED from terminal FAILED")
    void mapperSql_distinguishesRetryableAndTerminalFailures() throws NoSuchMethodException {
        Method claimMethod = AuditOutboxMapper.class.getMethod(
                "claimPending", String.class, int.class, int.class);
        Method reclaimMethod = AuditOutboxMapper.class.getMethod(
                "reclaimStaleClaimed", int.class);
        Method processMethod = AuditOutboxMapper.class.getMethod(
                "markProcessed", String.class, String.class);
        Method failureMethod = AuditOutboxMapper.class.getMethod(
                "markFailedWithRetry", String.class, String.class, String.class, int.class);
        String claimSql = String.join(" ", claimMethod.getAnnotation(Update.class).value());
        String reclaimSql = String.join(" ", reclaimMethod.getAnnotation(Update.class).value());
        String processSql = String.join(" ", processMethod.getAnnotation(Update.class).value());
        String failureSql = String.join(" ", failureMethod.getAnnotation(Update.class).value());

        assertThat(claimSql)
                .contains("state = 'FAILED'")
                .contains("attempts < #{maxAttempts}")
                .contains("next_retry_at <= NOW(3)");
        assertThat(failureSql)
                .contains("attempts + 1")
                .contains("processed_at = CASE")
                .contains("last_error = #{error}")
                .contains("next_retry_at = DATE_ADD(NOW(3), INTERVAL 30 SECOND)")
                .contains("#{maxAttempts}");
        assertThat(reclaimSql)
                .contains("attempts >= #{maxAttempts}")
                .contains("state = CASE")
                .contains("next_retry_at = CASE");
        assertThat(processSql)
                .contains("state = 'PROCESSING'")
                .contains("claim_owner = #{claimOwner}")
                .contains("state = 'PROCESSED'");
    }

    @Test
    @DisplayName("claim loss is reported without duplicating the audit log")
    void processRecordInNewTx_stopsDuplicateAfterClaimLoss() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("rec-race");
        record.setClaimOwner("owner-race");
        when(auditOutboxMapper.markProcessed(eq("rec-race"), eq("owner-race"))).thenReturn(0);

        assertThat(processor.processRecordInNewTx(record))
                .isEqualTo(AuditOutboxOutcome.LOST_CLAIM);

        verify(auditLogMapper, never()).insert(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }
}
