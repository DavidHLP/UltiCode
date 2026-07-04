package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
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
@DisplayName("WebSocketContestAnnouncementPushAdapter")
class WebSocketContestAnnouncementPushAdapterTest {

    @Mock
    private RealtimeService realtimeService;

    @InjectMocks
    private WebSocketContestAnnouncementPushAdapter adapter;

    @Test
    @DisplayName("emitAnnouncement delegates to RealtimeService.emitAnnouncement with payload")
    void emitAnnouncement_delegates() {
        AnnouncementPayload payload = AnnouncementPayload.of("a-1", "c-1", "Title", "Body");
        adapter.emitAnnouncement("c-1", payload);
        verify(realtimeService).emitAnnouncement(payload);
        verifyNoMoreInteractions(realtimeService);
    }
}