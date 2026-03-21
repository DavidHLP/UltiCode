package com.ulticode.modules.websocket.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ulticode.modules.websocket.contest.ContestRoomManager;
import com.ulticode.modules.websocket.dto.ContestRoomResponse;
import com.ulticode.modules.websocket.dto.SocketClientData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Tests for ContestGateway. */
@ExtendWith(MockitoExtension.class)
class ContestGatewayTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  @Mock private ContestRoomManager contestRoomManager;

  @Mock private SimpMessageHeaderAccessor headerAccessor;

  private ContestGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new ContestGateway(messagingTemplate, contestRoomManager);
  }

  @Test
  void handleJoinContest_withValidData_returnsSuccess() {
    String contestId = UUID.randomUUID().toString();
    SocketClientData userData = new SocketClientData("user-1", "username", "USER");
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put("user", userData);

    when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

    ContestRoomResponse response = gateway.handleJoinContest(contestId, headerAccessor);

    assertTrue(response.success());
    assertEquals(contestId, response.contestId());
    assertTrue(response.message().contains("Successfully joined"));
    verify(contestRoomManager).subscribe(contestId, "user-1");
  }

  @Test
  void handleJoinContest_withoutAuth_returnsError() {
    String contestId = UUID.randomUUID().toString();
    Map<String, Object> sessionAttributes = new HashMap<>();

    when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

    ContestRoomResponse response = gateway.handleJoinContest(contestId, headerAccessor);

    assertFalse(response.success());
    assertEquals("UNAUTHORIZED", response.error());
    verify(contestRoomManager, never()).subscribe(any(), any());
  }

  @Test
  void handleJoinContest_withInvalidContestId_returnsError() {
    String invalidContestId = "invalid";
    SocketClientData userData = new SocketClientData("user-1", "username", "USER");
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put("user", userData);

    when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

    ContestRoomResponse response = gateway.handleJoinContest(invalidContestId, headerAccessor);

    assertFalse(response.success());
    assertEquals("INVALID_CONTEST_ID", response.error());
    verify(contestRoomManager, never()).subscribe(any(), any());
  }

  @Test
  void handleLeaveContest_withValidData_returnsSuccess() {
    String contestId = UUID.randomUUID().toString();
    SocketClientData userData = new SocketClientData("user-1", "username", "USER");
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put("user", userData);

    when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

    ContestRoomResponse response = gateway.handleLeaveContest(contestId, headerAccessor);

    assertTrue(response.success());
    assertTrue(response.message().contains("Successfully left"));
    verify(contestRoomManager).unsubscribe(contestId, "user-1");
  }

  @Test
  void handlePing_returnsPongWithTimestamp() {
    ContestGateway.PongResponse response = gateway.handlePing(headerAccessor);

    assertTrue(response.timestamp() > 0);
  }
}
