package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Post-Candidate-4: the adapter now owns the {@link SimpMessagingTemplate}
 * call directly. Test pins: enum mapping (RUNNING→RUNNING, FINISHED→ENDED),
 * destination format ({@code /topic/contest/{id}/status}), payload
 * construction, and silent-skip for non-broadcast states.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketContestStatusPushAdapter")
class WebSocketContestStatusPushAdapterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketContestStatusPushAdapter adapter;

    @Test
    @DisplayName("RUNNING sends ContestStatusEvent to /topic/contest/{id}/status")
    void running_sendsToContestRoomStatus() {
        Instant start = Instant.parse("2026-07-04T10:00:00Z");
        adapter.emitStatus("c-1", ContestStatus.RUNNING, start, null, null);

        ArgumentCaptor<ContestStatusEvent> eventCaptor = ArgumentCaptor.forClass(ContestStatusEvent.class);
        verify(messagingTemplate).convertAndSend(
                eq(WebSocketUtils.getContestRoomName("c-1") + "/status"),
                eventCaptor.capture());
        ContestStatusEvent event = eventCaptor.getValue();
        assertThat(event.contestId()).isEqualTo("c-1");
        assertThat(event.status()).isEqualTo(ContestStatusEvent.ContestStatus.RUNNING);
        assertThat(event.startedAt()).isEqualTo(start);
    }

    @Test
    @DisplayName("FINISHED maps to wire ENDED")
    void finished_mapsToWireEnded() {
        Instant end = Instant.parse("2026-07-04T12:00:00Z");
        adapter.emitStatus("c-1", ContestStatus.FINISHED, null, end, "Contest over");

        ArgumentCaptor<ContestStatusEvent> eventCaptor = ArgumentCaptor.forClass(ContestStatusEvent.class);
        verify(messagingTemplate).convertAndSend(
                eq(WebSocketUtils.getContestRoomName("c-1") + "/status"),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(ContestStatusEvent.ContestStatus.ENDED);
    }

    @Test
    @DisplayName("DRAFT is silently skipped")
    void draft_silentlySkipped() {
        adapter.emitStatus("c-1", ContestStatus.DRAFT, null, null, null);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("UPCOMING is silently skipped")
    void upcoming_silentlySkipped() {
        adapter.emitStatus("c-1", ContestStatus.UPCOMING, null, null, null);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("CANCELLED is silently skipped")
    void cancelled_silentlySkipped() {
        adapter.emitStatus("c-1", ContestStatus.CANCELLED, null, null, null);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("null status is silently skipped")
    void nullStatus_silentlySkipped() {
        adapter.emitStatus("c-1", null, null, null, null);
        verifyNoInteractions(messagingTemplate);
    }

    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}