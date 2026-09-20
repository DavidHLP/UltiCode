package com.ulticode.common.storage;

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

    /** Marks the object store ready after the startup gate or a successful runtime request. */
    public void markReady() {
        this.state = State.READY;
        this.detail = null;
    }

    /** Marks the object store unavailable after a startup or runtime failure. */
    public void markFailed(String failureDetail) {
        this.state = State.FAILED;
        this.detail = failureDetail;
    }

    /** Marks verification as deliberately skipped (test profiles and operator opt-out). */
    public void markSkipped() {
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
}
