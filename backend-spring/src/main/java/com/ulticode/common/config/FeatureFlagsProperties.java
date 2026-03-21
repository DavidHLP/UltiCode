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
}
