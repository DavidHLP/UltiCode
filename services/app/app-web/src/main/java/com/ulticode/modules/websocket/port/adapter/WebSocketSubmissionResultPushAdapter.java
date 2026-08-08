package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.app.api.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link SubmissionResultPushPort}. Post-Candidate-4:
 * direct {@code SimpMessagingTemplate} call (see
 * {@link WebSocketNotificationPushAdapter} for the rationale).
 *
 * @author ulticode
 */
@Component
public class WebSocketSubmissionResultPushAdapter implements SubmissionResultPushPort {

    private final WebSocketBroadcastBridge broadcastBridge;

    public WebSocketSubmissionResultPushAdapter(WebSocketBroadcastBridge broadcastBridge) {
        this.broadcastBridge = broadcastBridge;
    }

    @Override
    public void emitSubmissionResult(String userId, SubmissionResultPayload payload) {
        broadcastBridge.sendToUser(
                userId, WebSocketConstants.USER_QUEUE_SUBMISSION, payload);
    }
}