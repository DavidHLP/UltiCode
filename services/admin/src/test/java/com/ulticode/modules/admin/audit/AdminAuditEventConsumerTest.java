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

        consumer.consume("audit-event-1", Map.ofEntries(
                Map.entry("auditId", "audit-event-1"),
                Map.entry("performerId", "admin-1"),
                Map.entry("userId", "user-1"),
                Map.entry("action", "UPDATE_CONTEST"),
                Map.entry("entityType", "CONTEST"),
                Map.entry("entityId", "contest-1"),
                Map.entry("oldValues", Map.of("title", "old")),
                Map.entry("newValues", Map.of("title", "new")),
                Map.entry("ipAddress", "127.0.0.1"),
                Map.entry("userAgent", "test-agent"),
                Map.entry("createdAt", "2026-08-31T10:15:30")));

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

        consumer.consume("audit-event-2", Map.of(
                "auditId", "audit-event-2",
                "performerId", "admin-1",
                "action", "READ_CONTEST",
                "entityType", "CONTEST",
                "entityId", "contest-1",
                "createdAt", "2026-08-31T10:15:30"));

        verify(auditLogMapper).insertIfAbsent(any(AuditLog.class));
    }

    @Test
    void rejectsMismatchedEventIdBeforeWriting() {
        assertThatThrownBy(() -> consumer.consume("audit-event-3", Map.of(
                "auditId", "different-id",
                "performerId", "admin-1",
                "action", "READ_CONTEST",
                "entityType", "CONTEST",
                "entityId", "contest-1",
                "createdAt", "2026-08-31T10:15:30")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(auditLogMapper, never()).insertIfAbsent(any(AuditLog.class));
    }
}
