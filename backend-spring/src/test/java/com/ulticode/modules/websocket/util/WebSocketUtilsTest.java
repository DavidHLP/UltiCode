package com.ulticode.modules.websocket.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests for WebSocketUtils. */
class WebSocketUtilsTest {

  @Test
  void isValidContestId_withValidUuid_returnsTrue() {
    String validUuid = UUID.randomUUID().toString();
    assertTrue(WebSocketUtils.isValidContestId(validUuid));
  }

  @Test
  void isValidContestId_withInvalidUuid_returnsFalse() {
    assertFalse(WebSocketUtils.isValidContestId("invalid-uuid"));
    assertFalse(WebSocketUtils.isValidContestId("12345"));
    assertFalse(WebSocketUtils.isValidContestId(""));
  }

  @Test
  void isValidContestId_withNull_returnsFalse() {
    assertFalse(WebSocketUtils.isValidContestId(null));
  }

  @Test
  void getContestRoomName_returnsCorrectFormat() {
    String contestId = UUID.randomUUID().toString();
    String roomName = WebSocketUtils.getContestRoomName(contestId);
    assertEquals("/topic/contest/" + contestId, roomName);
  }

  @Test
  void getUserQueueName_returnsCorrectFormat() {
    String userId = "user-123";
    String queueName = WebSocketUtils.getUserQueueName(userId);
    assertEquals("/user/user-123/queue", queueName);
  }
}
