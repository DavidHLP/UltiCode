package com.ulticode.auth.audit;

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
class AuthAuditSinkAdapterTest {

    @Mock
    private AuthAuditOutboxMapper authAuditOutboxMapper;

    private AuthAuditSinkAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuthAuditSinkAdapter(authAuditOutboxMapper, Clock.fixed(Instant.now(), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("log inserts a PENDING record into Auth audit_outbox")
    void log_insertsPendingRecordIntoOutbox() {
        adapter.log(
                "performer-123",
                "target-456",
                "RESET_PASSWORD",
                "USER",
                "target-456",
                Map.of("oldHash", "***"),
                Map.of("newHash", "***"),
                "192.168.1.1",
                "TestAgent"
        );

        ArgumentCaptor<AuthAuditOutboxRecord> captor = ArgumentCaptor.forClass(AuthAuditOutboxRecord.class);
        verify(authAuditOutboxMapper).insert(captor.capture());

        AuthAuditOutboxRecord saved = captor.getValue();
        assertThat(saved.getPerformerId()).isEqualTo("performer-123");
        assertThat(saved.getUserId()).isEqualTo("target-456");
        assertThat(saved.getAction()).isEqualTo("RESET_PASSWORD");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo("target-456");
        assertThat(saved.getState()).isEqualTo("PENDING");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getUserAgent()).isEqualTo("TestAgent");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("record targets the Auth-local audit_outbox table")
    void record_targetsLocalAuditOutbox() {
        com.baomidou.mybatisplus.annotation.TableName tableName =
                AuthAuditOutboxRecord.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
        assertThat(tableName.value()).isEqualTo("audit_outbox");
    }
}
