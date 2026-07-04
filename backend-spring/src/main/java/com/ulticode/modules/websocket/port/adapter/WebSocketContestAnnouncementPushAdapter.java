package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.admin.port.ContestAnnouncementPushPort;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketContestAnnouncementPushAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void emitAnnouncement(String contestId, AnnouncementPayload announcement) {
        String destination = WebSocketUtils.getContestRoomName(contestId) + "/announcement";
        messagingTemplate.convertAndSend(destination, announcement);
        log.info("Announcement sent to contest {}: {}", contestId, announcement.title());
    }
}