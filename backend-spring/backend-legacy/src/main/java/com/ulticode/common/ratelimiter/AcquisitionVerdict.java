package com.ulticode.common.ratelimiter;

/**
 * Verdict returned by {@link RateLimiter#tryAcquire}.
 *
 * <p>An {@link #allowed()} verdict carries no retry hint (the caller may
 * proceed immediately). A denied verdict carries {@link #retryAfterSeconds()}
 * so the caller can surface "try again in N seconds" to the user.
 *
 * @param allowed           true if the acquisition was permitted
 * @param retryAfterSeconds when denied, the seconds until the bucket resets;
 *                          zero when allowed
 */
public record AcquisitionVerdict(boolean allowed, long retryAfterSeconds) {

    /** Allowed verdict — no retry hint needed. */
    public static AcquisitionVerdict granted() {
        return new AcquisitionVerdict(true, 0L);
    }

    /**
     * Denied verdict.
     *
     * @param retryAfterSeconds seconds until the bucket resets; caller should clamp to ≥ 1
     * @return a denied verdict
     */
    public static AcquisitionVerdict denied(long retryAfterSeconds) {
        return new AcquisitionVerdict(false, Math.max(1L, retryAfterSeconds));
    }
}
