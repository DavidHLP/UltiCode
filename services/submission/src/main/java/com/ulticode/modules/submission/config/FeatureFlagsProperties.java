package com.ulticode.modules.submission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Feature flags configuration properties.
 *
 * <p>Configure via application.yml under app.features.*
 */
@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagsProperties {

  /** Enable first-solve notifications. */
  private boolean firstSolveNotificationsEnabled = true;

  /** Enable contest analytics generation. */
  private boolean contestAnalyticsEnabled = true;

  /**
   * Enable durable judge dispatch (ADR-003 M3a/M3c). When {@code true},
   * submit/rejudge/reaper write a {@code judge_outbox} row. With
   * {@code judge-queue.use-port=true}, the dispatcher sends those rows to
   * Redis Streams; with the port flag off, rows remain in shadow/legacy
   * compatibility mode. Default {@code false} preserves the pre-cutover path.
   */
  private boolean useJudgeOutbox = false;

  /**
   * Enable the generation fence + JUDGING lease mechanism (ADR-003 M3b). When
   * {@code true}, the worker claims submissions via a CAS {@code acquireLease},
   * heartbeats the lease while judging, and writes verdicts through
   * {@code writeVerdictFenced} so stale results from a superseded generation are
   * discarded. A {@link com.ulticode.modules.submission.reaper.JudgingLeaseReaper}
   * recovers crashed JUDGING rows. Default {@code false}; flag-off behavior is
   * byte-for-byte identical to the legacy selectById+updateById path.
   */
  private boolean useGenerationFence = false;

  /**
   * Nested properties for the M3c judge-queue port (ADR-003 M3c / ADR-005 §2.4).
   *
   * <p>P0-2 fix: these were previously flat fields ({@code judgeQueueUsePort} /
   * {@code judgeQueueEnvelopeVersion}) on this class, but {@code application.yml}
   * declares them under the nested key {@code app.features.judge-queue.*}.
   * Spring Boot {@code @ConfigurationProperties} flat binding does NOT map
   * {@code judge-queue.use-port} to {@code judgeQueueUsePort} — the
   * kebab→camel conversion only applies to leaf property names, not path
   * segments. The result was that YAML overrides never took effect: the
   * M3c cutover flag was silently stuck at the compile-time default
   * {@code false}, making the port path unreachable in production.
   *
   * <p>The fix groups the three judge-queue properties under a single
   * {@link NestedConfigurationProperty} so Spring's Binder recognises the
   * nested path. It also exposes the previously-dropped {@code cutover-at}
   * (F13 watermark) which the old Binder silently ignored.
   *
   * <p>Grouping (rather than a separate top-level
   * {@code @ConfigurationProperties(prefix="app.features.judge-queue")} class
   * like {@link JudgeSourceProperties}) keeps the whole {@code app.features}
   * subtree on one prefix, which makes the planned {@code FlagCombinationValidator}
   * (P1-1) a single-bean cross-check instead of a cross-class one.
   */
  @NestedConfigurationProperty
  private JudgeQueue judgeQueue = new JudgeQueue();

  public JudgeQueue getJudgeQueue() {
    return judgeQueue;
  }

  public void setJudgeQueue(JudgeQueue judgeQueue) {
    this.judgeQueue = judgeQueue;
  }

  /**
   * M3c judge-queue port properties (ADR-003 M3c / ADR-005 §2.4 / F13).
   */
  public static class JudgeQueue {
    /**
     * Route judge dispatches through the {@code JudgeQueue} port instead of
     * the legacy {@code RQueue.add}. Default {@code false}: the legacy
     * {@code RQueue} path remains the sole active producer.
     */
    private boolean usePort = false;

    /**
     * Envelope version the port writes. {@code 2} (default, matching both
     * shipped application.ymls' {@code APP_FEATURES_JUDGE_QUEUE_ENVELOPE_VERSION}
     * default) = adds {@code generation}/{@code attemptId} for the
     * fence-CAS write path. {@code 1} = legacy fields only, reachable only
     * via an explicit override; the dispatcher
     * ({@code JudgeOutboxDispatcher.toEnvelope}) hard-codes v2 and ignores
     * this flag until ADR-005 §2.4 consumes it.
     */
    private int envelopeVersion = 2;

    /**
     * F13 cutover watermark (ISO-8601 date-time). When set, only outbox rows
     * with {@code created_at >= cutoverAt} AND {@code is_shadow=0} are
     * real-dispatched through the port; older rows stay on the legacy RQueue
     * path. {@code null} (default) means no watermark — every non-shadow row
     * is dispatched through whichever path the {@code usePort} flag selects.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime cutoverAt;

    public boolean isUsePort() {
      return usePort;
    }

    public void setUsePort(boolean usePort) {
      this.usePort = usePort;
    }

    public int getEnvelopeVersion() {
      return envelopeVersion;
    }

    public void setEnvelopeVersion(int envelopeVersion) {
      this.envelopeVersion = envelopeVersion;
    }

    public LocalDateTime getCutoverAt() {
      return cutoverAt;
    }

    public void setCutoverAt(LocalDateTime cutoverAt) {
      this.cutoverAt = cutoverAt;
    }
  }

  // Getters and setters
  public boolean isFirstSolveNotificationsEnabled() {
    return firstSolveNotificationsEnabled;
  }

  public void setFirstSolveNotificationsEnabled(boolean firstSolveNotificationsEnabled) {
    this.firstSolveNotificationsEnabled = firstSolveNotificationsEnabled;
  }

  public boolean isContestAnalyticsEnabled() {
    return contestAnalyticsEnabled;
  }

  public void setContestAnalyticsEnabled(boolean contestAnalyticsEnabled) {
    this.contestAnalyticsEnabled = contestAnalyticsEnabled;
  }

  public boolean isUseJudgeOutbox() {
    return useJudgeOutbox;
  }

  public void setUseJudgeOutbox(boolean useJudgeOutbox) {
    this.useJudgeOutbox = useJudgeOutbox;
  }

  public boolean isUseGenerationFence() {
    return useGenerationFence;
  }

  public void setUseGenerationFence(boolean useGenerationFence) {
    this.useGenerationFence = useGenerationFence;
  }

}
