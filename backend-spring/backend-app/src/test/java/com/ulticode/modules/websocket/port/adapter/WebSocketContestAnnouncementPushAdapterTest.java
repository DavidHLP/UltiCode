package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.app.api.service.ContestAnnouncementPushPort;
import com.ulticode.app.api.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketContestAnnouncementPushAdapter")
class WebSocketContestAnnouncementPushAdapterTest {

    @Mock
    private WebSocketBroadcastBridge broadcastBridge;

    @InjectMocks
    private WebSocketContestAnnouncementPushAdapter adapter;

    @Test
    @DisplayName("emitAnnouncement sends to /topic/contest/{id}/announcement with the payload")
    void emitAnnouncement_sendsToContestRoomAnnouncement() {
        AnnouncementPayload payload = AnnouncementPayload.of("a-1", "c-1", "Title", "Body", "author-1");
        adapter.emitAnnouncement("c-1", payload);
        verify(broadcastBridge).send(
                eq(WebSocketUtils.getContestRoomName("c-1") + "/announcement"),
                eq(payload));
        verifyNoMoreInteractions(broadcastBridge);
    }
}
