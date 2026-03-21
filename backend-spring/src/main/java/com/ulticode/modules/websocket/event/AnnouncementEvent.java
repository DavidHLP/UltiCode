package com.ulticode.modules.websocket.event;

import java.time.Instant;

/**
 * Announcement event payload.
 *
 * <p>Sent when a new contest announcement is created.
 */
public record AnnouncementEvent(
    String id, String contestId, String title, String content, Instant createdAt) {}
