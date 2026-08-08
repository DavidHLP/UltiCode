package com.ulticode.modules.websocket.broadcast;

import com.ulticode.app.api.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload;
import com.ulticode.app.api.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Closed-string discriminator for {@link WebSocketBroadcastMessage} payloads.
 *
 * <p>This is the <strong>only</strong> mapping from a wire string to a deserialization target
 * class. Replacing the original {@code payloadClass} (an attacker-controlled FQCN resolved via
 * {@code Class.forName}) with this allowlist closes a Redis-channel deserialization sink: a
 * message carrying a {@code kind} outside this enum is dropped without any reflection or
 * Jackson polymorphic deserialization.
 *
 * <p>Add new broadcast payload types by extending this enum; never resolve a wire string to a
 * class via reflection elsewhere in the broadcast path.
 *
 * @author ulticode
 */
public enum WebSocketPayloadKind {
  NOTIFICATION("notification", NotificationPayload.class),
  BADGE_EARNED("badge_earned", BadgeEarnedPayload.class),
  ANNOUNCEMENT("announcement", AnnouncementPayload.class),
  RANKING_UPDATE("ranking_update", RankingUpdatePayload.class),
  SUBMISSION_RESULT("submission_result", SubmissionResultPayload.class),
  CONTEST_STATUS("contest_status", ContestStatusEvent.class);

  private static final Map<String, WebSocketPayloadKind> BY_WIRE = new HashMap<>();
  private static final Map<Class<?>, WebSocketPayloadKind> BY_CLASS = new HashMap<>();

  static {
    for (WebSocketPayloadKind kind : values()) {
      BY_WIRE.put(kind.wire, kind);
      BY_CLASS.put(kind.payloadClass, kind);
    }
  }

  private final String wire;
  private final Class<?> payloadClass;

  WebSocketPayloadKind(String wire, Class<?> payloadClass) {
    this.wire = wire;
    this.payloadClass = payloadClass;
  }

  public String wire() {
    return wire;
  }

  public Class<?> payloadClass() {
    return payloadClass;
  }

  /**
   * Resolve a wire discriminator to a registered kind. Returns {@code null} for any unknown
   * string; callers must check the result before using it and drop the message otherwise.
   *
   * @param wire wire string from an inbound Redis broadcast message
   * @return the matching kind, or {@code null} if the string is not in the allowlist
   */
  public static WebSocketPayloadKind fromWire(String wire) {
    if (wire == null) {
      return null;
    }
    return BY_WIRE.get(wire);
  }

  /**
   * Resolve a payload class to its registered kind for outbound serialization.
   *
   * @param payloadClass the runtime class of a payload about to be published
   * @return the matching kind
   * @throws IllegalArgumentException if the class was not registered (producer bug)
   */
  public static WebSocketPayloadKind fromClass(Class<?> payloadClass) {
    WebSocketPayloadKind kind = BY_CLASS.get(payloadClass);
    if (kind == null) {
      throw new IllegalArgumentException(
          "Payload class not registered for WS broadcast: " + payloadClass.getName()
              + ". Add it to WebSocketPayloadKind before publishing.");
    }
    return kind;
  }
}
