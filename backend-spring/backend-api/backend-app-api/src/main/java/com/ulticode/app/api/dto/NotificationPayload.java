package com.ulticode.app.api.dto;


import java.time.Instant;
import java.util.Map;
import java.io.Serializable;

/**
 * Notification payload for user notifications.
 *
 * <p>Sent to users for various notification types like mentions, replies, etc.
 */
public record NotificationPayload(
    String event,
    String id,
    String type,
    String title,
    String content,
    Map<String, Object> data,
    Instant createdAt,
    boolean read) implements Serializable {

  /** Notification types. */
  public static class NotificationType implements Serializable {
    public static final String MENTION = "mention";
    public static final String REPLY = "reply";
    public static final String SYSTEM = "system";
    public static final String CONTEST_REMINDER = "contest_reminder";
    public static final String PROBLEM_SOLVED = "problem_solved";
    public static final String ACHIEVEMENT = "achievement";
    public static final String COMMUNITY_UPDATE = "community_update";

    private NotificationType() {}
  }

  /**
   * Create a notification payload.
   *
   * @param id the notification ID
   * @param type the notification type
   * @param title the notification title
   * @param content the notification content
   * @param data additional data
   * @return notification payload
   */
  public static NotificationPayload of(
      String id,
      String type,
      String title,
      String content,
      Map<String, Object> data) {
    return new NotificationPayload(
        "notification",
        id,
        type,
        title,
        content,
        data,
        Instant.now(),
        false);
  }

  /**
   * Create a system notification.
   *
   * @param id the notification ID
   * @param title the notification title
   * @param content the notification content
   * @return notification payload
   */
  public static NotificationPayload system(String id, String title, String content) {
    return of(id, NotificationType.SYSTEM, title, content, null);
  }

  /**
   * Create a mention notification.
   *
   * @param id the notification ID
   * @param title the notification title
   * @param content the notification content
   * @param data additional data (e.g., post ID, username)
   * @return notification payload
   */
  public static NotificationPayload mention(String id, String title, String content, Map<String, Object> data) {
    return of(id, NotificationType.MENTION, title, content, data);
  }

  /**
   * Create a reply notification.
   *
   * @param id the notification ID
   * @param title the notification title
   * @param content the notification content
   * @param data additional data (e.g., comment ID, post ID)
   * @return notification payload
   */
  public static NotificationPayload reply(String id, String title, String content, Map<String, Object> data) {
    return of(id, NotificationType.REPLY, title, content, data);
  }
}
