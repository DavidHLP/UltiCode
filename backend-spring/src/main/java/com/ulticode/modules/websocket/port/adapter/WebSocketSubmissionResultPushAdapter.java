package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketSubmissionResultPushAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void emitSubmissionResult(String userId, SubmissionResultPayload payload) {
        messagingTemplate.convertAndSendToUser(
                userId, WebSocketConstants.USER_QUEUE_SUBMISSION, payload);
    }
}