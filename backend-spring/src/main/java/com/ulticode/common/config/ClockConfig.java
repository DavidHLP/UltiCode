package com.ulticode.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single source of truth for the JVM {@link Clock} bean — the deep module
 * that lets time-sensitive code be unit-tested without touching the wall
 * clock.
 *
 * <p>Prior to this bean, 84 sites across 30 service implementations called
 * {@link java.time.LocalDateTime#now()} directly. Every time-sensitive test
 * (subscription expiry, contest end, queue retry-backoff, audit timestamp,
 * achievement streak) fought the JVM wall clock — there was no injectable
 * seam. See {@code /tmp/architecture-review-1783420414.html} candidate 2.
 *
 * <p><strong>Migration pattern (phase 2 — mechanical, per service):</strong>
 * <pre>{@code
 * // before
 * @Service
 * @RequiredArgsConstructor
 * public class ContestServiceImpl implements ContestService {
 *     private final ContestMapper contestMapper;
 *
 *     public Contest start(String id) {
 *         Contest c = contestMapper.selectById(id);
 *         c.setStartedAt(LocalDateTime.now());          // ← wall clock
 *         ...
 *     }
 * }
 *
 * // after
 * @Service
 * @RequiredArgsConstructor
 * public class ContestServiceImpl implements ContestService {
 *     private final ContestMapper contestMapper;
 *     private final Clock clock;                         // ← injected
 *
 *     public Contest start(String id) {
 *         Contest c = contestMapper.selectById(id);
 *         c.setStartedAt(LocalDateTime.now(clock));     // ← fakeable
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Test impact (per migrated service):</strong> services using
 * Lombok {@code @RequiredArgsConstructor} gain a new constructor parameter.
 * Mockito {@code @InjectMocks} tests for those services must add a
 * corresponding {@code @Mock Clock clockField} OR use
 * {@code Clock.fixed(Instant, ZoneId)} for deterministic time assertions.
 * See {@code .agents/skills/mockito5-lombok-constructor-injection}.
 *
 * <p><strong>Migration target list (84 sites across 30 files, descending by
 * site count):</strong>
 * <ul>
 *   <li>ContestServiceImpl — 6 sites</li>
 *   <li>QueueServiceImpl — 6 sites</li>
 *   <li>AdminAnalyticsServiceImpl — 5-6 sites</li>
 *   <li>DashboardServiceImpl — 5 sites</li>
 *   <li>SolutionServiceImpl — 5 sites</li>
 *   <li>AdminUserAnalyticsServiceImpl — 4 sites</li>
 *   <li>EmailIntake — 1 site (line 121)</li>
 *   <li>NotificationServiceImpl — 1 site (line 165)</li>
 *   <li>…22 more Impl files…</li>
 * </ul>
 *
 * <p><strong>Production behavior unchanged.</strong> The bean returns
 * {@link Clock#systemDefaultZone()}, which is what {@code LocalDateTime.now()}
 * was using implicitly. Tests inject {@link Clock#fixed} for determinism.
 */
@Configuration
public class ClockConfig {

    /**
     * Production clock — uses the JVM default time-zone. Replace in tests
     * with {@link Clock#fixed(java.time.Instant, java.time.ZoneId)} via
     * {@code @Bean @Primary} or {@code @MockBean}.
     *
     * @return a {@link Clock} backed by the system clock
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
