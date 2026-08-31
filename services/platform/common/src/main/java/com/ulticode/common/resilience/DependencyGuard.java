package com.ulticode.common.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Process-local fail-fast bulkhead and consecutive-failure circuit breaker.
 *
 * <p>The transport still owns its timeout and safe retry count. Callers acquire
 * a permit before one logical dependency call and report success or failure
 * exactly once. Saturation and an open circuit reject immediately; no fallback
 * value is manufactured by this class.
 */
public final class DependencyGuard {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public enum Rejection {
        CIRCUIT_OPEN,
        SATURATED
    }

    public static final class RejectedException extends RuntimeException {

        private final Rejection reason;

        private RejectedException(Rejection reason) {
            super(reason == Rejection.CIRCUIT_OPEN
                    ? "dependency circuit is open"
                    : "dependency concurrency limit reached");
            this.reason = reason;
        }

        public Rejection reason() {
            return reason;
        }
    }

    public static final class Permit implements AutoCloseable {

        private final DependencyGuard owner;
        private final boolean halfOpenProbe;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Permit(DependencyGuard owner, boolean halfOpenProbe) {
            this.owner = owner;
            this.halfOpenProbe = halfOpenProbe;
        }

        public void success() {
            if (completed.compareAndSet(false, true)) {
                owner.completeSuccess(halfOpenProbe);
            }
        }

        public void failure() {
            if (completed.compareAndSet(false, true)) {
                owner.completeFailure(halfOpenProbe);
            }
        }

        public void ignore() {
            if (completed.compareAndSet(false, true)) {
                owner.completeIgnored(halfOpenProbe);
            }
        }

        @Override
        public void close() {
            ignore();
        }
    }

    private final int failureThreshold;
    private final long openMillis;
    private final LongSupplier clockMillis;
    private final Semaphore slots;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();

    public DependencyGuard(
            int maxConcurrentCalls, int failureThreshold, Duration openDuration) {
        this(maxConcurrentCalls, failureThreshold, openDuration, System::currentTimeMillis);
    }

    public DependencyGuard(
            int maxConcurrentCalls,
            int failureThreshold,
            Duration openDuration,
            LongSupplier clockMillis) {
        if (maxConcurrentCalls <= 0) {
            throw new IllegalArgumentException("maxConcurrentCalls must be positive");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        Duration delay = Objects.requireNonNull(openDuration, "openDuration");
        if (delay.isZero() || delay.isNegative() || delay.toMillis() == 0) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openMillis = delay.toMillis();
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        this.slots = new Semaphore(maxConcurrentCalls);
    }

    /** Acquire one logical dependency call or fail fast when unavailable. */
    public Permit acquire() {
        boolean probe = enterCircuit();
        if (!slots.tryAcquire()) {
            if (probe) {
                halfOpenProbe.set(false);
            }
            throw new RejectedException(Rejection.SATURATED);
        }
        inFlight.incrementAndGet();
        return new Permit(this, probe);
    }

    public State state() {
        return state.get();
    }

    public int inFlight() {
        return inFlight.get();
    }

    private boolean enterCircuit() {
        while (true) {
            State current = state.get();
            if (current == State.OPEN) {
                long elapsed = clockMillis.getAsLong() - openedAt.get();
                if (elapsed < openMillis) {
                    throw new RejectedException(Rejection.CIRCUIT_OPEN);
                }
                if (!state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    continue;
                }
                current = State.HALF_OPEN;
            }
            if (current == State.HALF_OPEN) {
                if (!halfOpenProbe.compareAndSet(false, true)) {
                    throw new RejectedException(Rejection.CIRCUIT_OPEN);
                }
                return true;
            }
            return false;
        }
    }

    private void completeSuccess(boolean probe) {
        if (probe) {
            consecutiveFailures.set(0);
            state.set(State.CLOSED);
        } else if (state.get() == State.CLOSED) {
            consecutiveFailures.set(0);
        }
        release(probe);
    }

    private void completeFailure(boolean probe) {
        if (probe) {
            consecutiveFailures.set(failureThreshold);
            openedAt.set(clockMillis.getAsLong());
            state.set(State.OPEN);
            release(true);
            return;
        }
        if (state.get() == State.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold) {
                openedAt.set(clockMillis.getAsLong());
                state.compareAndSet(State.CLOSED, State.OPEN);
            }
        }
        release(false);
    }

    private void completeIgnored(boolean probe) {
        release(probe);
    }

    private void release(boolean probe) {
        inFlight.decrementAndGet();
        slots.release();
        if (probe) {
            halfOpenProbe.set(false);
        }
    }
}
