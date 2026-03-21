package com.ulticode.modules.websocket.constants;

/**
 * Constants for WebSocket events and destinations.
 *
 * <p>Defines event names, topic prefixes, and queue names used throughout the WebSocket module.
 */
public final class WebSocketConstants {

  private WebSocketConstants() {
    // Utility class
  }

  // === Endpoint Paths ===
  public static final String ENDPOINT_CONTEST = "/ws/contest";
  public static final String ENDPOINT_NOTIFICATIONS = "/ws/notifications";

  // === Topic Prefixes ===
  public static final String TOPIC_PREFIX = "/topic";
  public static final String TOPIC_CONTEST = "/topic/contest";
  public static final String TOPIC_BROADCAST = "/topic/broadcast";

  // === Queue Prefixes ===
  public static final String QUEUE_PREFIX = "/queue";
  public static final String USER_QUEUE_NOTIFICATION = "/queue/notification";
  public static final String USER_QUEUE_SUBMISSION = "/queue/submission";
  public static final String USER_QUEUE_ERRORS = "/queue/errors";
  public static final String USER_QUEUE_CONTEST_RESPONSE = "/queue/contest.response";
  public static final String USER_QUEUE_PONG = "/queue/pong";

  // === Application Destination Prefixes ===
  public static final String APP_PREFIX = "/app";
  public static final String APP_CONTEST_JOIN = "/contest.join";
  public static final String APP_CONTEST_LEAVE = "/contest.leave";
  public static final String APP_NOTIFICATION_SUBSCRIBE = "/notification/subscribe";
  public static final String APP_PING = "/ping";

  // === Event Names ===
  public static final String EVENT_RANKING_UPDATE = "ranking_update";
  public static final String EVENT_FIRST_SOLVE = "first_solve";
  public static final String EVENT_ANNOUNCEMENT = "announcement";
  public static final String EVENT_CONTEST_STATUS = "contest_status";
  public static final String EVENT_SUBMISSION_RESULT = "submission_result";
  public static final String EVENT_NOTIFICATION = "notification";
  public static final String EVENT_BADGE_EARNED = "badge_earned";
  public static final String EVENT_COMMUNITY_UPDATE = "community_update";

  // === User Destination Prefix ===
  public static final String USER_DESTINATION_PREFIX = "/user";
}
