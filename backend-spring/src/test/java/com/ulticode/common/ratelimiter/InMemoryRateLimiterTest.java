package com.ulticode.common.ratelimiter;

import com.ulticode.common.time.FakeTimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InMemoryRateLimiter}. Exercises the
 * {@link com.ulticode.common.time.TimeSource} seam: the wall clock
 * is controlled by a {@link FakeTimeSource} so window-expiry tests
 * are deterministic and do not need to sleep.
 */
@DisplayName("InMemoryRateLimiter")
class InMemoryRateLimiterTest {

    private static final String BUCKET = "test-bucket";
    private static final int LIMIT = 3;
    private static final int PERIOD_SECONDS = 60;

    private FakeTimeSource fakeTime;
    private InMemoryRateLimiter limiter;

    @BeforeEach
    void setUp() {
        fakeTime = new FakeTimeSource(1_700_000_000_000L, 0L);
        limiter = new InMemoryRateLimiter(fakeTime);
    }

    @Test
    @DisplayName("first N requests within the window are granted (N = limit)")
    void firstNRequestsAreGranted() {
        for (int i = 0; i < LIMIT; i++) {
            AcquisitionVerdict v = limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
            assertTrue(v.allowed(), "request " + (i + 1) + " must be allowed");
        }
    }

    @Test
    @DisplayName("request beyond the limit is denied with a positive retryAfterSeconds")
    void requestBeyondLimitIsDenied() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        }
        AcquisitionVerdict v = limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        assertFalse(v.allowed(), "request beyond the limit must be denied");
        assertTrue(v.retryAfterSeconds() >= 1L, "retryAfterSeconds must be at least 1");
    }

    @Test
    @DisplayName("after the window elapses, the bucket resets and is granted again")
    void afterWindowElapsesBucketResets() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        }
        // Advance past the window
        fakeTime.advance(PERIOD_SECONDS * 1000L + 1L);
        AcquisitionVerdict v = limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        assertTrue(v.allowed(), "first request after window expiry must be allowed");
    }

    @Test
    @DisplayName("different buckets have independent counters")
    void differentBucketsAreIndependent() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        }
        // bucket A is full, but bucket B has its own counter
        assertFalse(limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS).allowed());
        assertTrue(limiter.tryAcquire("other-bucket", LIMIT, PERIOD_SECONDS).allowed());
    }

    @Test
    @DisplayName("reset() clears all buckets")
    void resetClearsAllBuckets() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        }
        assertFalse(limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS).allowed());
        limiter.reset();
        assertTrue(limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS).allowed());
    }

    @Test
    @DisplayName("TimeSource is read through the seam (not System.currentTimeMillis())")
    void timeSourceIsReadThroughTheSeam() {
        // Pin wall to a known instant and verify the limiter respects it
        fakeTime.pinWall(0L);
        // Exhaust the limit at wall = 0
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS);
        }
        // Inside the window but no capacity → denied (regardless of fake time)
        fakeTime.advance((PERIOD_SECONDS * 1000L) / 2L);
        assertFalse(limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS).allowed());
        // Advance just past the window → bucket resets, next request allowed
        fakeTime.advance((PERIOD_SECONDS * 1000L) / 2L + 1L);
        assertTrue(limiter.tryAcquire(BUCKET, LIMIT, PERIOD_SECONDS).allowed(),
                "the bucket must reset when the fake clock crosses the window boundary");
    }
}
