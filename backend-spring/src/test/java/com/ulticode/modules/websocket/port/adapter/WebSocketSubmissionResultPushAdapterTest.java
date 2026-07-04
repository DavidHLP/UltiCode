package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketSubmissionResultPushAdapter")
class WebSocketSubmissionResultPushAdapterTest {

    @Mock
    private RealtimeService realtimeService;

    @InjectMocks
    private WebSocketSubmissionResultPushAdapter adapter;

    @Test
    @DisplayName("emitSubmissionResult delegates to RealtimeService.emitSubmissionResult with same args")
    void emitSubmissionResult_delegates() {
        SubmissionResultPayload payload = SubmissionResultPayload.of(
                "s-1", null, "p-1", "u-1", "ACCEPTED", 0, 100, 1024);
        adapter.emitSubmissionResult("u-1", payload);
        verify(realtimeService).emitSubmissionResult("u-1", payload);
        verifyNoMoreInteractions(realtimeService);
    }
}