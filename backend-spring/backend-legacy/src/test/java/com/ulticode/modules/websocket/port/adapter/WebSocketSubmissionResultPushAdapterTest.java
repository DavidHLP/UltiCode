package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.app.api.dto.SubmissionResultPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketSubmissionResultPushAdapter")
class WebSocketSubmissionResultPushAdapterTest {

    @Mock
    private WebSocketBroadcastBridge broadcastBridge;

    @InjectMocks
    private WebSocketSubmissionResultPushAdapter adapter;

    @Test
    @DisplayName("emitSubmissionResult sends to /queue/submission with the userId + payload")
    void emitSubmissionResult_sendsToUserQueueSubmission() {
        SubmissionResultPayload payload = SubmissionResultPayload.of(
                "s-1", null, "p-1", "u-1", "ACCEPTED", 0, 100, 1024L);
        adapter.emitSubmissionResult("u-1", payload);
        verify(broadcastBridge).sendToUser(
                "u-1", WebSocketConstants.USER_QUEUE_SUBMISSION, payload);
        verifyNoMoreInteractions(broadcastBridge);
    }
}