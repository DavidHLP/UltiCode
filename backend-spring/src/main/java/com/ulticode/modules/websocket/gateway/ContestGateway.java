package com.ulticode.modules.websocket.gateway;

import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.contest.ContestRoomManager;
import com.ulticode.modules.websocket.dto.ContestRoomResponse;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * WebSocket Gateway for real-time contest events.
 *
 * <p>Provides real-time updates for:
 *
 * <ul>
 *   <li>Ranking updates
 *   <li>First solve notifications
 *   <li>Contest announcements
 *   <li>Contest status changes
 *   <li>Submission results
 * </ul>
 *
 * This gateway delegates to ContestWebSocketHandler for contest room management.
 */
@Controller
public class ContestGateway {

  private static final Logger log = LoggerFactory.getLogger(ContestGateway.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final ContestRoomManager contestRoomManager;

  public ContestGateway(
      SimpMessagingTemplate messagingTemplate,
      ContestRoomManager contestRoomManager) {
    this.messagingTemplate = messagingTemplate;
    this.contestRoomManager = contestRoomManager;
  }

  /**
   * Handle join contest request.
   *
   * @param contestId the contest ID
   * @param headerAccessor the message header accessor
   * @return response indicating success or failure
   */
  @MessageMapping(WebSocketConstants.APP_CONTEST_JOIN)
  @SendToUser(WebSocketConstants.USER_QUEUE_CONTEST_RESPONSE)
  public ContestRoomResponse handleJoinContest(
      @Payload String contestId, SimpMessageHeaderAccessor headerAccessor) {

    SocketClientData userData = getUserData(headerAccessor);

    if (userData == null) {
      return ContestRoomResponse.error(null, "UNAUTHORIZED", "You must be authenticated to join a contest");
    }

    if (!WebSocketUtils.isValidContestId(contestId)) {
      return ContestRoomResponse.error(contestId, "INVALID_CONTEST_ID", "Invalid contest ID format");
    }

    // Subscribe to contest room
    contestRoomManager.subscribe(contestId, userData.userId());

    log.debug("User {} joined contest {}", userData.username(), contestId);

    return ContestRoomResponse.success(contestId, "Successfully joined contest " + contestId);
  }

  /**
   * Handle leave contest request.
   *
   * @param contestId the contest ID
   * @param headerAccessor the message header accessor
   * @return response indicating success or failure
   */
  @MessageMapping(WebSocketConstants.APP_CONTEST_LEAVE)
  @SendToUser(WebSocketConstants.USER_QUEUE_CONTEST_RESPONSE)
  public ContestRoomResponse handleLeaveContest(
      @Payload String contestId, SimpMessageHeaderAccessor headerAccessor) {

    SocketClientData userData = getUserData(headerAccessor);

    if (userData == null) {
      return ContestRoomResponse.error(null, "UNAUTHORIZED", "You must be authenticated");
    }

    if (!WebSocketUtils.isValidContestId(contestId)) {
      return ContestRoomResponse.error(contestId, "INVALID_CONTEST_ID", "Invalid contest ID format");
    }

    // Unsubscribe from contest room
    contestRoomManager.unsubscribe(contestId, userData.userId());

    log.debug("User {} left contest {}", userData.username(), contestId);

    return ContestRoomResponse.success(contestId, "Successfully left contest " + contestId);
  }

  /**
   * Handle ping for connection keep-alive.
   *
   * @param headerAccessor the message header accessor
   * @return pong response
   */
  @MessageMapping(WebSocketConstants.APP_PING)
  @SendToUser(WebSocketConstants.USER_QUEUE_PONG)
  public PongResponse handlePing(SimpMessageHeaderAccessor headerAccessor) {
    return new PongResponse(System.currentTimeMillis());
  }

  /**
   * Handle exceptions from message handling.
   *
   * @param e the exception
   * @return error response
   */
  @MessageExceptionHandler
  @SendToUser(WebSocketConstants.USER_QUEUE_ERRORS)
  public String handleException(Exception e) {
    log.error("WebSocket error: {}", e.getMessage(), e);
    return "{\"error\":\"" + e.getMessage() + "\"}";
  }

  /**
   * Get user data from session attributes.
   *
   * @param headerAccessor the message header accessor
   * @return the user data or null if not authenticated
   */
  private SocketClientData getUserData(SimpMessageHeaderAccessor headerAccessor) {
    Object user = headerAccessor.getSessionAttributes().get("user");
    return user instanceof SocketClientData data ? data : null;
  }

  /** Pong response record. */
  public record PongResponse(long timestamp) {}
}
