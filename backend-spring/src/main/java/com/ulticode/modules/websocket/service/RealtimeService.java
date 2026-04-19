package com.ulticode.modules.websocket.service;

import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.contest.dto.FirstSolvePayload;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload.RankingItem;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import com.ulticode.modules.contest.service.RankingService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
  private final RankingService rankingService;

  /** Track last push time per contest for throttling. */
  private final Map<String, Long> lastRankingPushTime = new ConcurrentHashMap<>();

  /** Pending ranking updates that need to be pushed. */
  private final Map<String, Boolean> pendingRankingUpdates = new ConcurrentHashMap<>();

  public RealtimeService(SimpMessagingTemplate messagingTemplate, WebSocketProperties properties,
                        RankingService rankingService) {
    this.messagingTemplate = messagingTemplate;
    this.properties = properties;
    this.rankingService = rankingService;
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

    RankingUpdatePayload payload = RankingUpdatePayload.of(contestId, rankings);

    String destination = WebSocketUtils.getContestRoomName(contestId) + "/ranking";
    messagingTemplate.convertAndSend(destination, payload);

    log.debug("Emitted {} to {}", WebSocketConstants.EVENT_RANKING_UPDATE, destination);
  }

  /**
   * Emit first solve notification.
   *
   * @param payload the first solve payload
   */
  public void emitFirstSolve(FirstSolvePayload payload) {
    if (!properties.getFirstSolveNotifications().isEnabled()) {
      log.debug("Skipping first solve notification: Feature disabled");
      return;
    }

    String destination = WebSocketUtils.getContestRoomName(payload.contestId()) + "/first-solve";
    messagingTemplate.convertAndSend(destination, payload);

    log.info(
        "First solve: User {} solved problem {} in contest {}",
        payload.username(),
        payload.problemTitle(),
        payload.contestId());
  }

  /**
   * Emit announcement to contest room.
   *
   * @param payload the announcement payload
   */
  public void emitAnnouncement(AnnouncementPayload payload) {
    String destination = WebSocketUtils.getContestRoomName(payload.contestId()) + "/announcement";
    messagingTemplate.convertAndSend(destination, payload);

    log.info("Announcement sent to contest {}: {}", payload.contestId(), payload.title());
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
   * @param payload the submission result payload
   */
  public void emitSubmissionResult(String userId, SubmissionResultPayload payload) {
    messagingTemplate.convertAndSendToUser(userId, WebSocketConstants.USER_QUEUE_SUBMISSION, payload);

    log.debug("Submission result sent to user {}: {}", userId, payload.status());
  }

  /**
   * Mark a contest's ranking as dirty, requiring a flush on next throttle tick.
   *
   * @param contestId the contest ID
   */
  public void markDirty(String contestId) {
    pendingRankingUpdates.putIfAbsent(contestId, true);
  }

  /**
   * Flush pending ranking updates, emitting at most once per second per contest.
   * Called every second by the scheduler.
   */
  @Scheduled(fixedRate = 1000)
  public void flushPendingRankings() {
    Set<String> dirty = Set.copyOf(pendingRankingUpdates.keySet());
    pendingRankingUpdates.clear();

    for (String contestId : dirty) {
      Long lastPush = lastRankingPushTime.get(contestId);
      long elapsed = System.currentTimeMillis() - (lastPush != null ? lastPush : 0);

      if (elapsed >= RANKING_THROTTLE_MS) {
        List<RankingItem> rankings = rankingService.getLiveRanking(contestId, 200).stream()
                .map(vo -> new RankingItem(
                        vo.getRank() != null ? vo.getRank() : 0,
                        vo.getUserId() != null ? vo.getUserId().toString() : "",
                        vo.getUsername() != null ? vo.getUsername() : "",
                        vo.getScore() != null ? vo.getScore().doubleValue() : 0.0,
                        vo.getPenalty() != null ? vo.getPenalty().intValue() : 0,
                        vo.getProblemsSolved() != null ? vo.getProblemsSolved() : 0
                ))
                .collect(Collectors.toList());
        emitRankingUpdate(contestId, rankings);
        lastRankingPushTime.put(contestId, System.currentTimeMillis());
      } else {
        // Re-mark as dirty for next flush cycle
        pendingRankingUpdates.putIfAbsent(contestId, true);
      }
    }
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
