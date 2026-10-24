package com.ulticode.common.lifecycle;

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
    }

    /** Number of admitted cycles that have not finished yet. */
    public int inFlight() {
        return inFlight.get();
    }
}
