package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link SubmissionResultPushPort}.
 *
 * <p>Delegates to {@code RealtimeService.emitSubmissionResult} which already
 * handles the per-user destination and best-effort contract. After all
 * port extractions land this adapter becomes a one-line wrapper until
 * Candidate 4 collapses {@code RealtimeService} entirely.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSubmissionResultPushAdapter implements SubmissionResultPushPort {

    private final RealtimeService realtimeService;

    @Override
    public void emitSubmissionResult(String userId, SubmissionResultPayload payload) {
        realtimeService.emitSubmissionResult(userId, payload);
    }
}