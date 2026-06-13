package com.ulticode.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags configuration properties.
 *
 * <p>Configure via application.yml under app.features.*
 */
@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagsProperties {

  /** Enable new contest scoring system (point-based instead of Elo). */
  private boolean useNewContestSystem = false;

  /** Enable real-time ranking updates via WebSocket. */
  private boolean realtimeRankingEnabled = true;

  /** Enable first-solve notifications. */
  private boolean firstSolveNotificationsEnabled = true;

  /** Enable anti-cheat detection. */
  private boolean anticheatEnabled = false;

  /** Enable contest analytics generation. */
  private boolean contestAnalyticsEnabled = true;

  /**
   * Enable the judge outbox shadow write (ADR-003 M3a). When {@code true},
   * submit/rejudge/reaper write a {@code judge_outbox} row alongside the
   * existing RQueue enqueue, but the outbox dispatcher stays in shadow mode
   * (it never enqueues — the old RQueue remains the sole active producer).
   * Default {@code false} so the production path is unchanged until M3c cutover.
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
   * Route judge dispatches through the {@code JudgeQueue} port (ADR-003 M3c)
   * instead of the legacy {@code RQueue.add}. When {@code true}, the outbox
   * dispatcher hands each PENDING row to the port's {@code enqueue} method
   * and workers poll/ack through the port. Default {@code false}: the legacy
   * {@code RQueue} path remains the sole active producer. M3c-1 ships the
   * port interface; M3c-2 ships the Redisson Streams adapter; M3c-3 wires
   * the worker.
   */
  private boolean judgeQueueUsePort = false;

  /**
   * Envelope version the port writes (ADR-003 M3c / ADR-005 §2.4). When
   * {@code 1} (default), envelopes carry only the legacy job fields; when
   * {@code 2}, envelopes also carry {@code generation} and {@code attemptId}
   * so workers can run the fence-CAS write path. The port accepts both
   * versions on decode (dual-read), and writes whichever version this flag
   * names. Bumping this flag is the M3c-3 cutover for fence-aware dispatches.
   */
  private int judgeQueueEnvelopeVersion = 1;

  /**
   * Route notification dispatches through the {@code NotificationDispatcher}
   * (ADR-004 M4a). When {@code true}, business callers that have been
   * migrated build a {@link com.ulticode.modules.notification.intent.NotificationIntent}
   * and call {@code notificationDispatcher.dispatch(intent)}; the dispatcher
   * then fans out to the registered {@link com.ulticode.modules.notification.channel.NotificationChannel}
   * beans ({@code in_app} / {@code email} / {@code websocket}) with
   * per-channel {@code supports()} checks and ledger-backed idempotency.
   * When {@code false} (default), the legacy
   * {@code NotificationDispatchService} path stays active. The flag flips
   * caller-by-caller at M4c; once every caller is migrated, M4d deletes
   * the legacy service and removes this flag.
   */
  private boolean useNotificationIntent = false;

  // Getters and setters
  public boolean isUseNewContestSystem() {
    return useNewContestSystem;
  }

  public void setUseNewContestSystem(boolean useNewContestSystem) {
    this.useNewContestSystem = useNewContestSystem;
  }

  public boolean isRealtimeRankingEnabled() {
    return realtimeRankingEnabled;
  }

  public void setRealtimeRankingEnabled(boolean realtimeRankingEnabled) {
    this.realtimeRankingEnabled = realtimeRankingEnabled;
  }

  public boolean isFirstSolveNotificationsEnabled() {
    return firstSolveNotificationsEnabled;
  }

  public void setFirstSolveNotificationsEnabled(boolean firstSolveNotificationsEnabled) {
    this.firstSolveNotificationsEnabled = firstSolveNotificationsEnabled;
  }

  public boolean isAnticheatEnabled() {
    return anticheatEnabled;
  }

  public void setAnticheatEnabled(boolean anticheatEnabled) {
    this.anticheatEnabled = anticheatEnabled;
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

  public boolean isJudgeQueueUsePort() {
    return judgeQueueUsePort;
  }

  public void setJudgeQueueUsePort(boolean judgeQueueUsePort) {
    this.judgeQueueUsePort = judgeQueueUsePort;
  }

  public int getJudgeQueueEnvelopeVersion() {
    return judgeQueueEnvelopeVersion;
  }

  public void setJudgeQueueEnvelopeVersion(int judgeQueueEnvelopeVersion) {
    this.judgeQueueEnvelopeVersion = judgeQueueEnvelopeVersion;
  }

  public boolean isUseNotificationIntent() {
    return useNotificationIntent;
  }

  public void setUseNotificationIntent(boolean useNotificationIntent) {
    this.useNotificationIntent = useNotificationIntent;
  }
}
