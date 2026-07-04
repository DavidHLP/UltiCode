package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.contest.port.ContestRankingMarkDirtyPort;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link ContestRankingMarkDirtyPort}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketContestRankingMarkDirtyAdapter implements ContestRankingMarkDirtyPort {

    private final RealtimeService realtimeService;

    @Override
    public void markDirty(String contestId) {
        realtimeService.markDirty(contestId);
    }
}