package com.ulticode.modules.event.replay;

import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.ConsumerInboxRecord;
import com.ulticode.modules.event.outbox.IntegrationOutboxMapper;
import com.ulticode.modules.event.outbox.IntegrationOutboxRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * P6-REPLAY-001: Event replay / DLQ tooling tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P6-REPLAY-001: Event Replay + DLQ")
class EventReplayServiceTest {

    @Mock
    private IntegrationOutboxMapper outboxMapper;
    @Mock
    private ConsumerInboxMapper inboxMapper;

    @InjectMocks
    private EventReplayService service;

    private IntegrationOutboxRecord makeOutboxRecord(String eventId, String state, String aggregateId, int attempts) {
        IntegrationOutboxRecord r = new IntegrationOutboxRecord();
        r.setEventId(eventId);
        r.setState(state);
        r.setAggregateId(aggregateId);
        r.setAttempts(attempts);
        r.setLastError("poison error");
        return r;
    }

    private ConsumerInboxRecord makeInboxRecord(String id, String consumer, String eventId, String state) {
        ConsumerInboxRecord r = new ConsumerInboxRecord();
        r.setId(id);
        r.setConsumer(consumer);
        r.setEventId(eventId);
        r.setState(state);
        r.setAttempts(3);
        r.setLastError("handler crashed");
        return r;
    }

    @Nested
    @DisplayName("Outbox replay")
    class OutboxReplay {

        @Test
        @DisplayName("Replay resets DELIVERED/DEAD rows to PENDING")
        void replayResetsToPending() {
            when(outboxMapper.update(isNull(), any())).thenReturn(2);

            int count = service.replayOutbox(null);

            assertThat(count).isEqualTo(2);
            verify(outboxMapper).update(isNull(), any());
            verify(outboxMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("Replay by aggregateId filters correctly")
        void replayByAggregate() {
            when(outboxMapper.update(isNull(), any())).thenReturn(1);

            int count = service.replayOutbox("target-agg");

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Inbox replay")
    class InboxReplay {

        @Test
        @DisplayName("Replay resets PROCESSED/DEAD rows to PENDING for a consumer")
        void replayInboxByConsumer() {
            when(inboxMapper.update(isNull(), any())).thenReturn(2);

            int count = service.replayInbox("App", null);

            assertThat(count).isEqualTo(2);
            verify(inboxMapper).update(isNull(), any());
            verify(inboxMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("Replay specific event by eventId")
        void replaySpecificEvent() {
            when(inboxMapper.update(isNull(), any())).thenReturn(1);

            int count = service.replayInbox("App", "target-evt");

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DLQ management")
    class DLQManagement {

        @Test
        @DisplayName("List DEAD outbox events")
        void listDeadOutbox() {
            when(outboxMapper.selectList(any())).thenReturn(List.of(
                    makeOutboxRecord("dead-1", "DEAD", "agg-1", 5),
                    makeOutboxRecord("dead-2", "DEAD", "agg-2", 5)));

            var dead = service.listDeadOutbox();
            assertThat(dead).hasSize(2);
        }

        @Test
        @DisplayName("Clear DEAD outbox events (purge DLQ)")
        void clearDeadOutbox() {
            when(outboxMapper.delete(any())).thenReturn(1);

            int count = service.clearDeadOutbox();

            assertThat(count).isEqualTo(1);
            verify(outboxMapper).delete(any());
        }

        @Test
        @DisplayName("Re-route DEAD outbox events back to PENDING")
        void rerouteDeadOutbox() {
            when(outboxMapper.update(isNull(), any())).thenReturn(1);

            int count = service.rerouteDeadOutbox();

            assertThat(count).isEqualTo(1);
            verify(outboxMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("List DEAD inbox events for a consumer")
        void listDeadInbox() {
            when(inboxMapper.selectList(any())).thenReturn(List.of(
                    makeInboxRecord("in-1", "App", "evt-1", "DEAD")));

            var dead = service.listDeadInbox("App");
            assertThat(dead).hasSize(1);
        }
    }
}
