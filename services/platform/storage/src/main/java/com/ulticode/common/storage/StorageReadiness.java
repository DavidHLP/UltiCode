package com.ulticode.common.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Startup gate and runtime availability state of the mandatory object store, shared with owner readiness endpoints.
 *
 * <p>The gate itself runs during context refresh (see {@link StorageStartupProbe}), so a context that serves traffic has
 * already verified the object store. Runtime storage requests reuse this holder: successful requests recover it to
 * {@link State#READY}, while transport, service, and authentication failures move it to {@link State#FAILED}. A
 * missing object is a valid storage response and does not mark the store failed.
 */
public final class StorageReadiness {

    /** Verification lifecycle of the mandatory object store. */
    public enum State {
        /** The gate has not run yet; treat as not ready. */
        PENDING,
        /** The object store answered the bounded startup probe. */
        READY,
        /** The bounded startup probe exhausted its attempts. */
        FAILED,
        /** Verification was explicitly disabled ({@code app.storage.startup-probe.enabled=false}). */
        SKIPPED
    }

    private volatile State state = State.PENDING;
    private volatile String detail;
    private volatile Runnable recoveryProbe;
    private final AtomicBoolean recoveryProbeInFlight = new AtomicBoolean();

    /** Marks the object store ready after the startup gate or a successful runtime request. */
    public synchronized void markReady() {
        this.state = State.READY;
        this.detail = null;
    }

    /** Marks the object store unavailable after a startup or runtime failure. */
    public synchronized void markFailed(String failureDetail) {
        this.state = State.FAILED;
        this.detail = failureDetail;
    }

    /** Marks verification as deliberately skipped (test profiles and operator opt-out). */
    public synchronized void markSkipped() {
        this.state = State.SKIPPED;
        this.detail = "startup probe disabled by configuration";
    }

    public State state() {
        return state;
    }

    public String detail() {
        return detail;
    }

    /**
     * {@code true} when serving traffic is allowed: the object store was verified, or verification was explicitly
     * skipped. {@link State#PENDING} and {@link State#FAILED} report not-ready.
     */
    public boolean isReady() {
        return state == State.READY || state == State.SKIPPED;
    }

    /** Installs the bounded object-store probe used by failed readiness checks. */
    void setRecoveryProbe(Runnable recoveryProbe) {
        this.recoveryProbe = recoveryProbe;
    }

    private synchronized void markRecoveryProbeFailed(String failureDetail) {
        if (state == State.FAILED) {
            this.detail = failureDetail;
        }
    }

    /**
     * Schedules one bounded recovery probe when the store is failed.
     *
     * <p>The readiness endpoint remains fast and reports 503 until the probe
     * succeeds; concurrent health checks share the in-flight probe.
     */
    public void probeIfFailed() {
        if (state != State.FAILED) {
            return;
        }
        Runnable probe = recoveryProbe;
        if (probe == null || !recoveryProbeInFlight.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                probe.run();
            } catch (RuntimeException exception) {
                markRecoveryProbeFailed(exception.getMessage());
            } finally {
                recoveryProbeInFlight.set(false);
            }
        });
    }
}
