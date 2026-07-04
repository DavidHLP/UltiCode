package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.admin.port.ContestAnnouncementPushPort;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link ContestAnnouncementPushPort}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketContestAnnouncementPushAdapter implements ContestAnnouncementPushPort {

    private final RealtimeService realtimeService;

    @Override
    public void emitAnnouncement(String contestId, AnnouncementPayload announcement) {
        realtimeService.emitAnnouncement(announcement);
    }
}