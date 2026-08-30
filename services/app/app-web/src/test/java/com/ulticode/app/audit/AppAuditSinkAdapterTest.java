package com.ulticode.app.audit;

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
class AppAuditSinkAdapterTest {

    @Mock
    private AppAuditOutboxMapper appAuditOutboxMapper;

    private AppAuditSinkAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AppAuditSinkAdapter(appAuditOutboxMapper, Clock.fixed(Instant.now(), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("log inserts a PENDING record into App audit_outbox")
    void log_insertsPendingRecordIntoOutbox() {
        adapter.log(
                "performer-123",
                "target-456",
                "UPDATE_CONTEST",
                "CONTEST",
                "contest-789",
                Map.of("title", "old"),
                Map.of("title", "new"),
                "192.168.1.1",
                "TestAgent"
        );

        ArgumentCaptor<AppAuditOutboxRecord> captor = ArgumentCaptor.forClass(AppAuditOutboxRecord.class);
        verify(appAuditOutboxMapper).insert(captor.capture());

        AppAuditOutboxRecord saved = captor.getValue();
        assertThat(saved.getPerformerId()).isEqualTo("performer-123");
        assertThat(saved.getUserId()).isEqualTo("target-456");
        assertThat(saved.getAction()).isEqualTo("UPDATE_CONTEST");
        assertThat(saved.getEntityType()).isEqualTo("CONTEST");
        assertThat(saved.getEntityId()).isEqualTo("contest-789");
        assertThat(saved.getOldValues()).containsEntry("title", "old");
        assertThat(saved.getNewValues()).containsEntry("title", "new");
        assertThat(saved.getState()).isEqualTo("PENDING");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getUserAgent()).isEqualTo("TestAgent");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("record targets the App-local audit_outbox table")
    void record_targetsLocalAuditOutbox() {
        com.baomidou.mybatisplus.annotation.TableName tableName =
                AppAuditOutboxRecord.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
        assertThat(tableName.value()).isEqualTo("audit_outbox");
    }
}
