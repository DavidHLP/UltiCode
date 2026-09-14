package com.ulticode.modules.admin.port.adapter;

import com.ulticode.common.time.FakeTimeSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AdminQueryDeadlineTest {

    @Test
    void remainingBudgetUsesTheMonotonicTimeSource() {
        FakeTimeSource timeSource = new FakeTimeSource(0L, 100L);
        AdminQueryDeadline deadlinePolicy = new AdminQueryDeadline(timeSource);

        AdminQueryDeadline.Deadline deadline = deadlinePolicy.start(800, TimeUnit.NANOSECONDS);

        assertThat(deadline.remainingNanos()).isEqualTo(800L);
        timeSource.advanceNanos(250L);
        assertThat(deadline.remainingNanos()).isEqualTo(550L);
        timeSource.advanceNanos(550L);
        assertThat(deadline.remainingNanos()).isZero();
    }

    @Test
    void nonPositiveBudgetIsImmediatelyExpired() {
        FakeTimeSource timeSource = new FakeTimeSource();

        assertThat(new AdminQueryDeadline(timeSource)
                .start(0, TimeUnit.MILLISECONDS)
                .remainingNanos()).isZero();
        assertThat(new AdminQueryDeadline(timeSource)
                .start(-1, TimeUnit.MILLISECONDS)
                .remainingNanos()).isZero();
    }
}
