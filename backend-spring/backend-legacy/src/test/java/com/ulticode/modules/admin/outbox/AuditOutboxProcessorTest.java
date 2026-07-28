package com.ulticode.modules.admin.outbox;

import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
    @DisplayName("processRecordInNewTx inserts AuditLog and marks outbox processed")
    void processRecordInNewTx_insertsLogAndMarksProcessed() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("rec-100");
        record.setPerformerId("perf-1");
        record.setUserId("user-1");
        record.setAction("TEST_ACTION");
        record.setEntityType("TEST_ENTITY");
        record.setEntityId("ent-1");
        record.setOldValues(Map.of("k1", "v1"));
        record.setNewValues(Map.of("k2", "v2"));
        record.setIpAddress("127.0.0.1");
        record.setUserAgent("Mozilla");

        processor.processRecordInNewTx(record);

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(logCaptor.capture());

        AuditLog auditLog = logCaptor.getValue();
        assertThat(auditLog.getPerformerId()).isEqualTo("perf-1");
        assertThat(auditLog.getAction()).isEqualTo("TEST_ACTION");
        assertThat(auditLog.getEntityId()).isEqualTo("ent-1");

        verify(auditOutboxMapper).markProcessed("rec-100");
    }

    @Test
    @DisplayName("markFailedInNewTx delegates to mapper")
    void markFailedInNewTx_delegatesToMapper() {
        processor.markFailedInNewTx("rec-200");

        verify(auditOutboxMapper).markFailed("rec-200");
    }
}
