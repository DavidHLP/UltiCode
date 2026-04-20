package com.ulticode.modules.websocket.service;

import static org.mockito.Mockito.*;

import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.contest.dto.FirstSolvePayload;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload.RankingItem;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus;
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
  @Mock private com.ulticode.modules.contest.service.RankingService rankingService;

  private RealtimeService realtimeService;
  private WebSocketProperties properties;

  @BeforeEach
  void setUp() {
    properties = new WebSocketProperties();
    realtimeService = new RealtimeService(messagingTemplate, properties, rankingService);
  }

  @Test
  void emitRankingUpdate_whenEnabled_sendsMessage() {
    String contestId = "contest-123";
    List<RankingItem> rankings = Collections.emptyList();

    realtimeService.emitRankingUpdate(contestId, rankings);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/" + contestId + "/ranking"), any(RankingUpdatePayload.class));
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
    FirstSolvePayload payload =
        FirstSolvePayload.of(
            "contest-123", "problem-1", "Problem 1", "user-1", "username");

    realtimeService.emitFirstSolve(payload);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/contest-123/first-solve"), eq(payload));
  }

  @Test
  void emitFirstSolve_whenDisabled_doesNotSend() {
    properties.getFirstSolveNotifications().setEnabled(false);
    FirstSolvePayload payload =
        FirstSolvePayload.of(
            "contest-123", "problem-1", "Problem 1", "user-1", "username");

    realtimeService.emitFirstSolve(payload);

    verifyNoInteractions(messagingTemplate);
  }

  @Test
  void emitAnnouncement_sendsMessage() {
    AnnouncementPayload payload =
        AnnouncementPayload.of(
            "announcement-1", "contest-123", "Title", "Content");

    realtimeService.emitAnnouncement(payload);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/contest-123/announcement"), eq(payload));
  }

  @Test
  void emitContestStatus_sendsMessage() {
    realtimeService.emitContestStatus("contest-123", ContestStatus.RUNNING, null, null, null);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/contest/contest-123/status"), any(ContestStatusEvent.class));
  }

  @Test
  void emitSubmissionResult_sendsMessageToUser() {
    SubmissionResultPayload payload =
        SubmissionResultPayload.of(
            "sub-1", "contest-123", "problem-1", "user-1", "Accepted", 100.0, 50, 1024L);

    realtimeService.emitSubmissionResult("user-1", payload);

    verify(messagingTemplate).convertAndSendToUser(eq("user-1"), eq("/queue/submission"), eq(payload));
  }

  @Test
  void broadcastToAll_sendsMessageToTopic() {
    Object data = new Object();

    realtimeService.broadcastToAll("test-event", data);

    verify(messagingTemplate).convertAndSend("/topic/broadcast/test-event", data);
  }
}
