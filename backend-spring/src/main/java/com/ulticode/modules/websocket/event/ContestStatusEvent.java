package com.ulticode.modules.websocket.event;

import java.time.Instant;

/**
 * Contest status update event payload.
 *
 * <p>Sent when contest status changes (upcoming, registration, running, ended).
 */
public record ContestStatusEvent(
    String contestId,
    ContestStatus status,
    Instant startedAt,
    Instant endsAt,
    String message) {

  /** Contest status enumeration. */
  public enum ContestStatus {
    UPCOMING,
    REGISTRATION,
    RUNNING,
    ENDED
  }
}
