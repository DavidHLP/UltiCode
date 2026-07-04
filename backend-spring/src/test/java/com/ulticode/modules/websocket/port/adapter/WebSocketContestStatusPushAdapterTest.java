package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketContestStatusPushAdapter")
class WebSocketContestStatusPushAdapterTest {

    @Mock
    private RealtimeService realtimeService;

    @InjectMocks
    private WebSocketContestStatusPushAdapter adapter;

    @Test
    @DisplayName("RUNNING maps to wire RUNNING and forwards all args")
    void running_mapsToWireRunning() {
        Instant start = Instant.parse("2026-07-04T10:00:00Z");
        adapter.emitStatus("c-1", ContestStatus.RUNNING, start, null, null);
        verify(realtimeService).emitContestStatus("c-1",
                ContestStatusEvent.ContestStatus.RUNNING, start, null, null);
    }

    @Test
    @DisplayName("FINISHED maps to wire ENDED")
    void finished_mapsToWireEnded() {
        Instant end = Instant.parse("2026-07-04T12:00:00Z");
        adapter.emitStatus("c-1", ContestStatus.FINISHED, null, end, "Contest over");
        verify(realtimeService).emitContestStatus("c-1",
                ContestStatusEvent.ContestStatus.ENDED, null, end, "Contest over");
    }

    @Test
    @DisplayName("DRAFT is silently skipped - no wire push")
    void draft_silentlySkipped() {
        adapter.emitStatus("c-1", ContestStatus.DRAFT, null, null, null);
        verifyNoInteractions(realtimeService);
    }

    @Test
    @DisplayName("UPCOMING is silently skipped - no wire push")
    void upcoming_silentlySkipped() {
        adapter.emitStatus("c-1", ContestStatus.UPCOMING, null, null, null);
        verifyNoInteractions(realtimeService);
    }

    @Test
    @DisplayName("CANCELLED is silently skipped - no wire push")
    void cancelled_silentlySkipped() {
        adapter.emitStatus("c-1", ContestStatus.CANCELLED, null, null, null);
        verifyNoInteractions(realtimeService);
    }

    @Test
    @DisplayName("null status is silently skipped")
    void nullStatus_silentlySkipped() {
        adapter.emitStatus("c-1", null, null, null, null);
        verifyNoInteractions(realtimeService);
    }
}