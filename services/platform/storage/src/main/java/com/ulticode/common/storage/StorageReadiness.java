package com.ulticode.common.storage;

/**
 * Outcome of the object-store startup gate, shared with the owner readiness endpoints.
 *
 * <p>The gate itself runs during context refresh (see {@link StorageStartupProbe}), so a context that serves traffic has
 * already verified the object store. This holder exists so readiness reports what actually happened instead of assuming
 * success, and so an operator can see why a boot was refused.
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

    /** Marks the object store verified. */
    public void markReady() {
        this.state = State.READY;
        this.detail = null;
    }

    /** Marks the object store unverified after a failed gate. */
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
