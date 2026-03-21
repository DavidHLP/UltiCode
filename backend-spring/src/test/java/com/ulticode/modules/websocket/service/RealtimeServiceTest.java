package com.ulticode.modules.websocket.service;

import static org.mockito.Mockito.*;

import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.event.AnnouncementEvent;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus;
import com.ulticode.modules.websocket.event.FirstSolveEvent;
import com.ulticode.modules.websocket.event.RankingUpdateEvent;
import com.ulticode.modules.websocket.event.RankingUpdateEvent.RankingItem;
import com.ulticode.modules.websocket.event.SubmissionResultEvent;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Tests for RealtimeService. */
@ExtendWith(MockitoExtension.class)
class RealtimeServiceTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  private RealtimeService realtimeService;
  private WebSocketProperties properties;

  @BeforeEach
  void setUp() {
    properties = new WebSocketProperties();
    realtimeService = new RealtimeService(messagingTemplate, properties);
  }

  @Test
  void emitRankingUpdate_whenEnabled_sendsMessage() {
    String contestId = "contest-123";
    List<RankingItem> rankings = Collections.emptyList();

    realtimeService.emitRankingUpdate(contestId, rankings);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/" + contestId + "/ranking"), any(RankingUpdateEvent.class));
  }

  @Test
  void emitRankingUpdate_whenDisabled_doesNotSend() {
    properties.getRealtimeRanking().setEnabled(false);
    String contestId = "contest-123";

    realtimeService.emitRankingUpdate(contestId, Collections.emptyList());

    verifyNoInteractions(messagingTemplate);
  }

  @Test
  void emitFirstSolve_whenEnabled_sendsMessage() {
    FirstSolveEvent event =
        new FirstSolveEvent(
            "contest-123", "problem-1", "Problem 1", "user-1", "username", Instant.now());

    realtimeService.emitFirstSolve(event);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/contest-123/first-solve"), eq(event));
  }

  @Test
  void emitFirstSolve_whenDisabled_doesNotSend() {
    properties.getFirstSolveNotifications().setEnabled(false);
    FirstSolveEvent event =
        new FirstSolveEvent(
            "contest-123", "problem-1", "Problem 1", "user-1", "username", Instant.now());

    realtimeService.emitFirstSolve(event);

    verifyNoInteractions(messagingTemplate);
  }

  @Test
  void emitAnnouncement_sendsMessage() {
    AnnouncementEvent event =
        new AnnouncementEvent(
            "announcement-1", "contest-123", "Title", "Content", Instant.now());

    realtimeService.emitAnnouncement(event);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/contest-123/announcement"), eq(event));
  }

  @Test
  void emitContestStatus_sendsMessage() {
    realtimeService.emitContestStatus("contest-123", ContestStatus.RUNNING, null, null, null);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/contest-123/status"), any(ContestStatusEvent.class));
  }

  @Test
  void emitSubmissionResult_sendsMessageToUser() {
    SubmissionResultEvent event =
        new SubmissionResultEvent(
            "sub-1", "contest-123", "problem-1", "user-1", "Accepted", 100.0, 50, 1024L, Instant.now());

    realtimeService.emitSubmissionResult("user-1", event);

    verify(messagingTemplate).convertAndSendToUser(eq("user-1"), eq("/queue/submission"), eq(event));
  }

  @Test
  void broadcastToAll_sendsMessageToTopic() {
    Object data = new Object();

    realtimeService.broadcastToAll("test-event", data);

    verify(messagingTemplate).convertAndSend("/topic/broadcast/test-event", data);
  }
}
