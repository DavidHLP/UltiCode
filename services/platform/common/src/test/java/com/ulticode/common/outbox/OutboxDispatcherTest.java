package com.ulticode.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutboxDispatcherTest {

    @Test
    void publishesAndConfirmsEachClaimedRecord() {
        Adapter adapter = new Adapter();
        OutboxDispatcher<Event> dispatcher = new OutboxDispatcher<>("test", adapter);

        assertThat(dispatcher.dispatch()).isEqualTo(1);
        assertThat(adapter.published.get()).isEqualTo(1);
        assertThat(adapter.delivered.get()).isEqualTo(1);
        assertThat(adapter.failed.get()).isZero();
    }

    @Test
    void marksARecordFailedWhenPublicationThrows() {
        Adapter adapter = new Adapter();
        adapter.fail = true;
        OutboxDispatcher<Event> dispatcher = new OutboxDispatcher<>("test", adapter);

        assertThat(dispatcher.dispatch()).isZero();
        assertThat(adapter.delivered.get()).isZero();
        assertThat(adapter.failed.get()).isEqualTo(1);
    }

    private record Event(String id) {
    }

    private static final class Adapter implements OutboxDispatcher.Adapter<Event> {
        private final AtomicInteger published = new AtomicInteger();
        private final AtomicInteger delivered = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private boolean fail;

        @Override
        public void reclaimStaleClaimed() {
        }

        @Override
        public int claimPending(String claimOwner, int limit) {
            return 1;
        }

        @Override
        public List<Event> selectClaimed(String claimOwner) {
            return List.of(new Event("event-1"));
        }

        @Override
        public String publish(Event record) {
            if (fail) {
                throw new IllegalStateException("publish failed");
            }
            published.incrementAndGet();
            return "publication-1";
        }

        @Override
        public int markDelivered(Event record, String claimOwner, String publicationId) {
            delivered.incrementAndGet();
            return 1;
        }

        @Override
        public int markFailed(Event record, String claimOwner, String error, int maxAttempts) {
            failed.incrementAndGet();
            return 1;
        }

        @Override
        public String recordId(Event record) {
            return record.id();
        }
    }
}
