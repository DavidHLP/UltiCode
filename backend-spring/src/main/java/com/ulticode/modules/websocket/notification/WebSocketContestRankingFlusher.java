package com.ulticode.modules.websocket.notification;

import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload;
import com.ulticode.modules.websocket.contest.dto.RankingUpdatePayload.RankingItem;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Throttled flusher of contest ranking updates to the leaderboard.
 *
 * <p>Owns the producer-side concern that did not fit a single
 * {@code SimpMessagingTemplate.convertAndSend} call: rankings update
 * many times per second, the throttle limits one push per second per
 * contest, and the cleanup sweep prevents the in-memory maps from
 * growing unbounded.
 *
 * <p>Extracted verbatim from the old {@code RealtimeService} (now
 * deleted) when the rest of the realtime-push seam was inverted into
 * per-consumer ports. ADR-0009 §3 explains why this class stays in the
 * websocket module rather than the contest module: the throttle logic
 * exists only to protect the WebSocket transport, not the contest
 * domain.
 *
 * @author ulticode
 */
@Component
public class WebSocketContestRankingFlusher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketContestRankingFlusher.class);

    /** Throttle interval for ranking updates (1 second). */
    private static final long RANKING_THROTTLE_MS = 1000;

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketProperties properties;
    private final RankingService rankingService;

    /** Track last push time per contest for throttling. */
    private final Map<String, Long> lastRankingPushTime = new ConcurrentHashMap<>();

    /** Pending ranking updates that need to be pushed. */
    private final Map<String, Boolean> pendingRankingUpdates = new ConcurrentHashMap<>();

    public WebSocketContestRankingFlusher(SimpMessagingTemplate messagingTemplate,
                                          WebSocketProperties properties,
                                          RankingService rankingService) {
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
        this.rankingService = rankingService;
    }

    /**
     * Mark a contest's ranking as dirty. Called by
     * {@code WebSocketContestRankingMarkDirtyAdapter} from the contest
     * module's {@code ContestRankingMarkDirtyPort}.
     *
     * @param contestId the contest id
     */
    public void markDirty(String contestId) {
        pendingRankingUpdates.putIfAbsent(contestId, true);
    }

    /**
     * Flush pending ranking updates, emitting at most once per second
     * per contest. Scheduled every second.
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

    private void emitRankingUpdate(String contestId, List<RankingItem> rankings) {
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