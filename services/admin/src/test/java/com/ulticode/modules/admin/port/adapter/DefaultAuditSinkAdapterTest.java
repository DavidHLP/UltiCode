package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;
import com.ulticode.modules.admin.outbox.AuditOutboxRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultAuditSinkAdapterTest {

    @Mock
    private AuditOutboxMapper auditOutboxMapper;

    private DefaultAuditSinkAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultAuditSinkAdapter(auditOutboxMapper, Clock.fixed(Instant.now(), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("log inserts a PENDING record into audit_outbox table")
    void log_insertsPendingRecordIntoOutbox() {
        adapter.log(
                "performer-123",
                "target-456",
                "UPDATE_PASSWORD",
                "USER",
                "target-456",
                Map.of("oldHash", "***"),
                Map.of("newHash", "***"),
                "192.168.1.1",
                "TestAgent"
        );

        ArgumentCaptor<AuditOutboxRecord> captor = ArgumentCaptor.forClass(AuditOutboxRecord.class);
        verify(auditOutboxMapper).insert(captor.capture());

        AuditOutboxRecord saved = captor.getValue();
        assertThat(saved.getPerformerId()).isEqualTo("performer-123");
        assertThat(saved.getUserId()).isEqualTo("target-456");
        assertThat(saved.getAction()).isEqualTo("UPDATE_PASSWORD");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo("target-456");
        assertThat(saved.getState()).isEqualTo("PENDING");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getUserAgent()).isEqualTo("TestAgent");
    }
}
