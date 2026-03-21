package com.ulticode.modules.websocket.constants;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for WebSocketConstants. */
class WebSocketConstantsTest {

  @Test
  void endpointConstants_areCorrect() {
    assertEquals("/ws/contest", WebSocketConstants.ENDPOINT_CONTEST);
    assertEquals("/ws/notifications", WebSocketConstants.ENDPOINT_NOTIFICATIONS);
  }

  @Test
  void topicConstants_areCorrect() {
    assertEquals("/topic", WebSocketConstants.TOPIC_PREFIX);
    assertEquals("/topic/contest", WebSocketConstants.TOPIC_CONTEST);
    assertEquals("/topic/broadcast", WebSocketConstants.TOPIC_BROADCAST);
  }

  @Test
  void queueConstants_areCorrect() {
    assertEquals("/queue", WebSocketConstants.QUEUE_PREFIX);
    assertEquals("/queue/notification", WebSocketConstants.USER_QUEUE_NOTIFICATION);
    assertEquals("/queue/submission", WebSocketConstants.USER_QUEUE_SUBMISSION);
    assertEquals("/queue/errors", WebSocketConstants.USER_QUEUE_ERRORS);
    assertEquals("/queue/contest.response", WebSocketConstants.USER_QUEUE_CONTEST_RESPONSE);
    assertEquals("/queue/pong", WebSocketConstants.USER_QUEUE_PONG);
  }

  @Test
  void appConstants_areCorrect() {
    assertEquals("/app", WebSocketConstants.APP_PREFIX);
    assertEquals("/contest.join", WebSocketConstants.APP_CONTEST_JOIN);
    assertEquals("/contest.leave", WebSocketConstants.APP_CONTEST_LEAVE);
    assertEquals("/notification/subscribe", WebSocketConstants.APP_NOTIFICATION_SUBSCRIBE);
    assertEquals("/ping", WebSocketConstants.APP_PING);
  }

  @Test
  void eventConstants_areCorrect() {
    assertEquals("ranking_update", WebSocketConstants.EVENT_RANKING_UPDATE);
    assertEquals("first_solve", WebSocketConstants.EVENT_FIRST_SOLVE);
    assertEquals("announcement", WebSocketConstants.EVENT_ANNOUNCEMENT);
    assertEquals("contest_status", WebSocketConstants.EVENT_CONTEST_STATUS);
    assertEquals("submission_result", WebSocketConstants.EVENT_SUBMISSION_RESULT);
    assertEquals("notification", WebSocketConstants.EVENT_NOTIFICATION);
    assertEquals("badge_earned", WebSocketConstants.EVENT_BADGE_EARNED);
    assertEquals("community_update", WebSocketConstants.EVENT_COMMUNITY_UPDATE);
  }

  @Test
  void userDestinationPrefix_isCorrect() {
    assertEquals("/user", WebSocketConstants.USER_DESTINATION_PREFIX);
  }
}
