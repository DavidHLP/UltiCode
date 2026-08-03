package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.AnnouncementPayload;

/**
 * Port for broadcasting contest announcements to WebSocket subscribers.
 * Promoted from admin.port for P7-RELOCATE-WEBSOCKET-001.
 */
public interface ContestAnnouncementPushPort {
    void emitAnnouncement(String contestId, AnnouncementPayload announcement);
}
