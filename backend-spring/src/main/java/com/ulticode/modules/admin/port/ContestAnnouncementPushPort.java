package com.ulticode.modules.admin.port;

import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;

/**
 * Announcement-push port the admin module uses to broadcast a contest
 * announcement to its WebSocket subscribers when an admin creates one.
 *
 * <p>Replaces the cross-module leak point
 * {@code AdminContestServiceImpl.createAnnouncement} had on
 * {@code com.ulticode.modules.websocket.service.RealtimeService.emitAnnouncement}
 * (and its {@code AnnouncementPayload} import).
 *
 * <p>Contract: best-effort, fire-and-forget (D-12). The announcement
 * row in the database is the durable record; the WebSocket push is the
 * live signal to currently-subscribed clients.
 *
 * @author ulticode
 */
public interface ContestAnnouncementPushPort {

    /**
     * Push a contest announcement envelope to the contest room subscribers.
     *
     * <p>Implementations MUST NOT throw on a missing subscription.
     *
     * @param contestId     the contest id (must not be {@code null})
     * @param announcement  the wire-format announcement envelope (must not be {@code null})
     */
    void emitAnnouncement(String contestId, AnnouncementPayload announcement);
}