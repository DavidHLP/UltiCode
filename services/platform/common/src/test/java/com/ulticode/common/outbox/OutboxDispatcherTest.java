package com.ulticode.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        assertThat(adapter.failedOwner.get()).isEqualTo(adapter.claimOwner.get());
        assertThat(adapter.failedError.get()).isEqualTo("publish failed");
        assertThat(adapter.failedMaxAttempts.get()).isEqualTo(OutboxDispatcher.MAX_ATTEMPTS);
    }

    @Test
    void doesNotRetryWhenDeliveryConfirmationLosesTheFence() {
        Adapter adapter = new Adapter();
        adapter.deliveryResult = 0;
        OutboxDispatcher<Event> dispatcher = new OutboxDispatcher<>("test", adapter);

        assertThat(dispatcher.dispatch()).isZero();
        assertThat(adapter.delivered.get()).isEqualTo(1);
        assertThat(adapter.failed.get()).isZero();
    }

    @Test
    void refusesNewCyclesAfterDrainBegins() {
        Adapter adapter = new Adapter();
        OutboxDispatcher<Event> dispatcher = new OutboxDispatcher<>("test", adapter);

        dispatcher.beginDrain();

        assertThat(dispatcher.dispatch()).isZero();
        assertThat(adapter.claimed.get()).isZero();
    }

    private record Event(String id) {
    }

    private static final class Adapter implements OutboxDispatcher.Adapter<Event> {
        private final AtomicInteger published = new AtomicInteger();
        private final AtomicInteger delivered = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger claimed = new AtomicInteger();
        private final AtomicReference<String> claimOwner = new AtomicReference<>();
        private final AtomicReference<String> failedOwner = new AtomicReference<>();
        private final AtomicReference<String> failedError = new AtomicReference<>();
        private final AtomicInteger failedMaxAttempts = new AtomicInteger();
        private boolean fail;
        private int deliveryResult = 1;

        @Override
        public void reclaimStaleClaimed() {
        }

        @Override
        public int claimPending(String claimOwner, int limit) {
            claimed.incrementAndGet();
            this.claimOwner.set(claimOwner);
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
            return deliveryResult;
        }

        @Override
        public int markFailed(Event record, String claimOwner, String error, int maxAttempts) {
            failed.incrementAndGet();
            failedOwner.set(claimOwner);
            failedError.set(error);
            failedMaxAttempts.set(maxAttempts);
            return 1;
        }

        @Override
        public String recordId(Event record) {
            return record.id();
        }
    }
}
