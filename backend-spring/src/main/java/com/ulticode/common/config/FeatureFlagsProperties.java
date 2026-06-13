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
}
