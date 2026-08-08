package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link WebSocketContestStatusPushAdapter}.
 *
 * <p>P7-RELOCATE-CONTEST-001: adapter signature changed from
 * {@code (String, ContestStatus, Instant, Instant, String)} to
 * {@code (String, String, Long, Long, String)}. Tests updated to
 * pass String statusName + Long epoch-millis.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketContestStatusPushAdapter")
class WebSocketContestStatusPushAdapterTest {

    @Mock
    private WebSocketBroadcastBridge broadcastBridge;

    @InjectMocks
    private WebSocketContestStatusPushAdapter adapter;

    @Test
    @DisplayName("RUNNING sends ContestStatusEvent to /topic/contest/{id}/status")
    void running_sendsToContestRoomStatus() {
        long startMs = 1783257600000L; // 2026-07-04T10:00:00Z
        adapter.emitStatus("c-1", "RUNNING", startMs, null, null);

        ArgumentCaptor<ContestStatusEvent> eventCaptor = ArgumentCaptor.forClass(ContestStatusEvent.class);
        verify(broadcastBridge).send(
                eq(WebSocketUtils.getContestRoomName("c-1") + "/status"),
                eventCaptor.capture());
        ContestStatusEvent event = eventCaptor.getValue();
        assertThat(event.contestId()).isEqualTo("c-1");
        assertThat(event.status()).isEqualTo(ContestStatusEvent.ContestStatus.RUNNING);
        assertThat(event.startedAt().toEpochMilli()).isEqualTo(startMs);
    }

    @Test
    @DisplayName("FINISHED maps to wire ENDED")
    void finished_mapsToWireEnded() {
        long endMs = 1783264800000L; // 2026-07-04T12:00:00Z
        adapter.emitStatus("c-1", "FINISHED", null, endMs, "Contest over");

        ArgumentCaptor<ContestStatusEvent> eventCaptor = ArgumentCaptor.forClass(ContestStatusEvent.class);
        verify(broadcastBridge).send(
                eq(WebSocketUtils.getContestRoomName("c-1") + "/status"),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(ContestStatusEvent.ContestStatus.ENDED);
    }

    @Test
    @DisplayName("DRAFT is silently skipped")
    void draft_silentlySkipped() {
        adapter.emitStatus("c-1", "DRAFT", null, null, null);
        verifyNoInteractions(broadcastBridge);
    }

    @Test
    @DisplayName("UPCOMING is silently skipped")
    void upcoming_silentlySkipped() {
        adapter.emitStatus("c-1", "UPCOMING", null, null, null);
        verifyNoInteractions(broadcastBridge);
    }

    @Test
    @DisplayName("CANCELLED is silently skipped")
    void cancelled_silentlySkipped() {
        adapter.emitStatus("c-1", "CANCELLED", null, null, null);
        verifyNoInteractions(broadcastBridge);
    }

    @Test
    @DisplayName("null status is silently skipped")
    void nullStatus_silentlySkipped() {
        adapter.emitStatus("c-1", null, null, null, null);
        verifyNoInteractions(broadcastBridge);
    }

    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
