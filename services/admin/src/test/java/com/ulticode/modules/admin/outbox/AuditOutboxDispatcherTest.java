package com.ulticode.modules.admin.outbox;
import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditOutboxDispatcherTest {

    @Mock
    private AuditOutboxMapper auditOutboxMapper;

    @Mock
    private AuditOutboxProcessor auditOutboxProcessor;

    private AdminAuditOutboxPublisher publisher;
    private AuditOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        publisher = new AdminAuditOutboxPublisher(auditOutboxProcessor);
        dispatcher = new AuditOutboxDispatcher(auditOutboxMapper, auditOutboxProcessor, publisher);
    }

    @Test
    @DisplayName("dispatch returns 0 when no pending records exist")
    void dispatch_returnsZeroWhenNoPendingRecords() {
        when(auditOutboxMapper.claimPending(anyString(), anyInt())).thenReturn(0);

        int count = dispatcher.dispatch();

        assertThat(count).isEqualTo(0);
        verify(auditOutboxProcessor, never()).processRecordInNewTx(any());
    }

    @Test
    @DisplayName("admin publisher sinks locally and returns no stream id")
    void publisher_sinksLocallyAndReturnsNull() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("outbox-local");

        assertThat(publisher.publish(record)).isNull();

        verify(auditOutboxProcessor).processRecordInNewTx(record);
    }

    @Test
    @DisplayName("dispatch delegates each pending outbox record to processor")
    void dispatch_delegatesToProcessor() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("outbox-1");

        when(auditOutboxMapper.claimPending(anyString(), anyInt())).thenReturn(1);
        when(auditOutboxMapper.selectClaimed(anyString())).thenAnswer(invocation -> {
            record.setClaimOwner(invocation.getArgument(0));
            return List.of(record);
        });

        int count = dispatcher.dispatch();

        assertThat(count).isEqualTo(1);
        verify(auditOutboxProcessor).processRecordInNewTx(record);
        assertThat(record.getClaimOwner()).isNotNull();
    }

    @Test
    @DisplayName("dispatch catches exception during processing and marks record as failed")
    void dispatch_marksFailedOnException() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("outbox-err");

        when(auditOutboxMapper.claimPending(anyString(), anyInt())).thenReturn(1);
        when(auditOutboxMapper.selectClaimed(anyString())).thenAnswer(invocation -> {
            record.setClaimOwner(invocation.getArgument(0));
            return List.of(record);
        });
        doThrow(new RuntimeException("DB error")).when(auditOutboxProcessor).processRecordInNewTx(record);

        int count = dispatcher.dispatch();

        assertThat(count).isEqualTo(0);
        verify(auditOutboxProcessor).markFailedInNewTx(eq("outbox-err"), anyString());
    }

    @Test
    @DisplayName("dispatch skips a row claimed by another dispatcher")
    void dispatch_skipsAlreadyClaimedRecord() {
        AuditOutboxRecord record = new AuditOutboxRecord();
        record.setId("outbox-race");

        when(auditOutboxMapper.claimPending(anyString(), anyInt())).thenReturn(0);

        int count = dispatcher.dispatch();

        assertThat(count).isZero();
        verify(auditOutboxProcessor, never()).processRecordInNewTx(any());
        verify(auditOutboxProcessor, never()).markFailedInNewTx(anyString(), anyString());
    }
}
