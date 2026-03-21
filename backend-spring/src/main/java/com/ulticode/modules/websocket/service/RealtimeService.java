package com.ulticode.modules.websocket.service;

import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.event.AnnouncementEvent;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus;
import com.ulticode.modules.websocket.event.FirstSolveEvent;
import com.ulticode.modules.websocket.event.RankingUpdateEvent;
import com.ulticode.modules.websocket.event.RankingUpdateEvent.RankingItem;
import com.ulticode.modules.websocket.event.SubmissionResultEvent;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Real-time service for pushing contest updates via WebSocket.
 *
 * <p>Wraps SimpMessagingTemplate to provide higher-level methods for:
 *
 * <ul>
 *   <li>Pushing ranking updates (throttled to max once per second)
 *   <li>First solve notifications
 *   <li>Announcements
 *   <li>Contest status changes
 *   <li>Submission results
 * </ul>
 */
@Service
public class RealtimeService {

  private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);

  /** Throttle interval for ranking updates (1 second). */
  private static final long RANKING_THROTTLE_MS = 1000;

  private final SimpMessagingTemplate messagingTemplate;
  private final WebSocketProperties properties;

  /** Track last push time per contest for throttling. */
  private final Map<String, Long> lastRankingPushTime = new ConcurrentHashMap<>();

  /** Pending ranking updates that need to be pushed. */
  private final Map<String, Boolean> pendingRankingUpdates = new ConcurrentHashMap<>();

  public RealtimeService(SimpMessagingTemplate messagingTemplate, WebSocketProperties properties) {
    this.messagingTemplate = messagingTemplate;
    this.properties = properties;
  }

  /**
   * Emit ranking update to all clients subscribed to a contest room.
   *
   * @param contestId the contest ID
   * @param rankings the ranking items
   */
  public void emitRankingUpdate(String contestId, List<RankingItem> rankings) {
    if (!properties.getRealtimeRanking().isEnabled()) {
      log.debug("Skipping ranking update: Feature disabled for contest {}", contestId);
      return;
    }

    RankingUpdateEvent event =
        new RankingUpdateEvent(contestId, rankings, Instant.now());

    String destination = WebSocketUtils.getContestRoomName(contestId) + "/ranking";
    messagingTemplate.convertAndSend(destination, event);

    log.debug("Emitted {} to {}", WebSocketConstants.EVENT_RANKING_UPDATE, destination);
  }

  /**
   * Emit first solve notification.
   *
   * @param event the first solve event
   */
  public void emitFirstSolve(FirstSolveEvent event) {
    if (!properties.getFirstSolveNotifications().isEnabled()) {
      log.debug("Skipping first solve notification: Feature disabled");
      return;
    }

    String destination = WebSocketUtils.getContestRoomName(event.contestId()) + "/first-solve";
    messagingTemplate.convertAndSend(destination, event);

    log.info(
        "First solve: User {} solved problem {} in contest {}",
        event.username(),
        event.problemTitle(),
        event.contestId());
  }

  /**
   * Emit announcement to contest room.
   *
   * @param event the announcement event
   */
  public void emitAnnouncement(AnnouncementEvent event) {
    String destination = WebSocketUtils.getContestRoomName(event.contestId()) + "/announcement";
    messagingTemplate.convertAndSend(destination, event);

    log.info("Announcement sent to contest {}: {}", event.contestId(), event.title());
  }

  /**
   * Emit contest status update.
   *
   * @param contestId the contest ID
   * @param status the new status
   * @param startedAt optional start time
   * @param endsAt optional end time
   * @param message optional message
   */
  public void emitContestStatus(
      String contestId,
      ContestStatus status,
      Instant startedAt,
      Instant endsAt,
      String message) {

    ContestStatusEvent event =
        new ContestStatusEvent(contestId, status, startedAt, endsAt, message);

    String destination = WebSocketUtils.getContestRoomName(contestId) + "/status";
    messagingTemplate.convertAndSend(destination, event);

    log.info("Contest {} status changed to: {}", contestId, status);
  }

  /**
   * Emit submission result to a specific user.
   *
   * @param userId the user ID
   * @param event the submission result event
   */
  public void emitSubmissionResult(String userId, SubmissionResultEvent event) {
    messagingTemplate.convertAndSendToUser(userId, WebSocketConstants.USER_QUEUE_SUBMISSION, event);

    log.debug("Submission result sent to user {}: {}", userId, event.status());
  }

  /**
   * Broadcast to all connected clients.
   *
   * @param event the event name
   * @param data the data to broadcast
   */
  public void broadcastToAll(String event, Object data) {
    messagingTemplate.convertAndSend(WebSocketConstants.TOPIC_BROADCAST + "/" + event, data);
  }

  /**
   * Send notification to a specific user.
   *
   * @param userId the user ID
   * @param notification the notification payload
   */
  public void sendNotification(String userId, Object notification) {
    messagingTemplate.convertAndSendToUser(userId, WebSocketConstants.USER_QUEUE_NOTIFICATION, notification);

    log.debug("Notification sent to user {}", userId);
  }

  /**
   * Clean up old throttle tracking entries periodically.
   */
  @Scheduled(fixedRate = 60000)
  public void cleanupThrottleTracking() {
    long cutoff = System.currentTimeMillis() - 60000; // 1 minute ago

    lastRankingPushTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    pendingRankingUpdates.entrySet().removeIf(entry -> {
      String contestId = entry.getKey();
      return !lastRankingPushTime.containsKey(contestId);
    });
  }
}
