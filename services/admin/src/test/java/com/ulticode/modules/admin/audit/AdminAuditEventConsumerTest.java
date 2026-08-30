package com.ulticode.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuditEventConsumerTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    private AdminAuditEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AdminAuditEventConsumer(auditLogMapper);
    }

    @Test
    void appliesValidOwnerAuditEventWithEventIdAsPrimaryKey() {
        when(auditLogMapper.insertIfAbsent(any(AuditLog.class))).thenReturn(1);

        consumer.consume("audit-event-1", new AdminAuditRecordedPayload(
                "audit-event-1", "admin-1", "user-1", "UPDATE_CONTEST", "CONTEST", "contest-1",
                Map.of("title", "old"), Map.of("title", "new"), "127.0.0.1", "test-agent",
                "2026-08-31T10:15:30"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insertIfAbsent(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("audit-event-1");
        assertThat(saved.getPerformerId()).isEqualTo("admin-1");
        assertThat(saved.getAction()).isEqualTo("UPDATE_CONTEST");
        assertThat(saved.getOldValues()).containsEntry("title", "old");
        assertThat(saved.getCreatedAt()).hasToString("2026-08-31T10:15:30");
    }

    @Test
    void acceptsDuplicateInsertAsAnIdempotentReplay() {
        when(auditLogMapper.insertIfAbsent(any(AuditLog.class))).thenReturn(0);

        consumer.consume("audit-event-2", new AdminAuditRecordedPayload(
                "audit-event-2", "admin-1", null, "READ_CONTEST", "CONTEST", "contest-1",
                null, null, null, null, "2026-08-31T10:15:30"));

        verify(auditLogMapper).insertIfAbsent(any(AuditLog.class));
    }

    @Test
    void rejectsMismatchedEventIdBeforeWriting() {
        assertThatThrownBy(() -> consumer.consume("audit-event-3", new AdminAuditRecordedPayload(
                "different-id", "admin-1", null, "READ_CONTEST", "CONTEST", "contest-1",
                null, null, null, null, "2026-08-31T10:15:30")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(auditLogMapper, never()).insertIfAbsent(any(AuditLog.class));
    }
}
