package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.port.ContestRankingMarkDirtyPort;
import com.ulticode.modules.websocket.notification.WebSocketContestRankingFlusher;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link ContestRankingMarkDirtyPort}.
 *
 * <p>Post-Candidate-4: the mark-dirty flag + throttled flush are owned
 * by {@link WebSocketContestRankingFlusher} (extracted from the old
 * {@code RealtimeService}). The adapter is a one-line delegation so the
 * producer-side throttle logic stays internal to the websocket module.
 *
 * @author ulticode
 */
@Component
public class WebSocketContestRankingMarkDirtyAdapter implements ContestRankingMarkDirtyPort {

    private final WebSocketContestRankingFlusher flusher;

    public WebSocketContestRankingMarkDirtyAdapter(WebSocketContestRankingFlusher flusher) {
        this.flusher = flusher;
    }

    @Override
    public void markDirty(String contestId) {
        flusher.markDirty(contestId);
    }
}