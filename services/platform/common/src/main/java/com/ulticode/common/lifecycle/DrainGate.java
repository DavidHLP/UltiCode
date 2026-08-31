package com.ulticode.common.lifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small process-local gate for graceful shutdown.
 *
 * <p>Callers enter before claiming new work and leave after the whole bounded
 * cycle finishes. Shutdown flips the gate first; existing work may drain,
 * while later claims are rejected and remain recoverable in their PEL/lease.
 */
public final class DrainGate {

    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();

    /** Atomically admit one new work cycle unless shutdown has started. */
    public boolean tryEnter() {
        if (draining.get()) {
            return false;
        }
        inFlight.incrementAndGet();
        if (draining.get()) {
            leave();
            return false;
        }
        return true;
    }

    /** Mark this process as draining; the operation is idempotent. */
    public void beginDrain() {
        draining.set(true);
        signalWaiters();
    }

    /** Return whether new work must be refused. */
    public boolean isDraining() {
        return draining.get();
    }

    /** Finish one admitted cycle. */
    public void leave() {
        int remaining = inFlight.decrementAndGet();
        if (remaining < 0) {
            inFlight.incrementAndGet();
            throw new IllegalStateException("drain gate leave without enter");
        }
        if (remaining == 0) {
            signalWaiters();
        }
    }

    /** Number of admitted cycles that have not finished yet. */
    public int inFlight() {
        return inFlight.get();
    }

    /** Wait for admitted work to finish, returning false on timeout. */
    public boolean awaitDrained(Duration timeout) throws InterruptedException {
        Duration waitFor = Objects.requireNonNull(timeout, "timeout");
        if (waitFor.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        long remainingNanos = waitFor.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        synchronized (this) {
            while (inFlight.get() > 0) {
                if (remainingNanos <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
                remainingNanos = deadline - System.nanoTime();
            }
            return true;
        }
    }

    private void signalWaiters() {
        synchronized (this) {
            notifyAll();
        }
    }
}
