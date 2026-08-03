package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.app.api.service.ContestAnnouncementPushPort;
import com.ulticode.app.api.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link ContestAnnouncementPushPort}.
 *
 * <p>Post-Candidate-4: direct {@code SimpMessagingTemplate} call.
 *
 * @author ulticode
 */
@Component
public class WebSocketContestAnnouncementPushAdapter implements ContestAnnouncementPushPort {

    private static final Logger log = LoggerFactory.getLogger(WebSocketContestAnnouncementPushAdapter.class);

    private final WebSocketBroadcastBridge broadcastBridge;

    public WebSocketContestAnnouncementPushAdapter(WebSocketBroadcastBridge broadcastBridge) {
        this.broadcastBridge = broadcastBridge;
    }

    @Override
    public void emitAnnouncement(String contestId, AnnouncementPayload announcement) {
        String destination = WebSocketUtils.getContestRoomName(contestId) + "/announcement";
        broadcastBridge.send(destination, announcement);
        log.info("Announcement sent to contest {}: {}", contestId, announcement.title());
    }
}