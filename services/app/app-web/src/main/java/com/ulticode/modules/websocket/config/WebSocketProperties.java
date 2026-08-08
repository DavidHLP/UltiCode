package com.ulticode.modules.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket configuration properties.
 *
 * <p>Configure via application.yml under app.websocket.*
 */
@Configuration
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

  /** Enable real-time ranking feature. */
  private FeatureConfig realtimeRanking = new FeatureConfig(true);

  /** Enable first solve notifications feature. */
  private FeatureConfig firstSolveNotifications = new FeatureConfig(true);

  /** Broadcast configuration for multi-instance STOMP relay. */
  private BroadcastConfig broadcast = new BroadcastConfig();

  /** Allowed CORS origins for WebSocket connections. Reads from shared cors.allowed-origins property. */
  @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:http://localhost:9002,http://localhost:9003}")
  private String[] allowedOrigins = {"http://localhost:9002", "http://localhost:9003"};

  // Getters and setters
  public FeatureConfig getRealtimeRanking() {
    return realtimeRanking;
  }

  public void setRealtimeRanking(FeatureConfig realtimeRanking) {
    this.realtimeRanking = realtimeRanking;
  }

  public FeatureConfig getFirstSolveNotifications() {
    return firstSolveNotifications;
  }

  public void setFirstSolveNotifications(FeatureConfig firstSolveNotifications) {
    this.firstSolveNotifications = firstSolveNotifications;
  }

  public String[] getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }
  public BroadcastConfig getBroadcast() {
    return broadcast;
  }

  public void setBroadcast(BroadcastConfig broadcast) {
    this.broadcast = broadcast;
  }


  /** Feature configuration with enabled flag. */
  public static class FeatureConfig {
    private boolean enabled;

    public FeatureConfig() {}

    public FeatureConfig(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** Broadcast configuration. */
  public static class BroadcastConfig {
    private boolean enabled = false;
    private String channel = "ulticode:ws:broadcast";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getChannel() {
      return channel;
    }

    public void setChannel(String channel) {
      this.channel = channel;
    }
  }
}
