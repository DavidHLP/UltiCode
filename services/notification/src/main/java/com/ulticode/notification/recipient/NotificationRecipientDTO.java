package com.ulticode.notification.recipient;

import java.io.Serializable;

/**
 * Minimum recipient projection required by notification delivery.
 *
 * <p>Moved from {@code com.ulticode.app.api.dto} — Notification owns this
 * in-process seam and its DTO.
 */
public record NotificationRecipientDTO(
        String userId,
        String email,
        boolean active,
        boolean banned) implements Serializable {
    private static final long serialVersionUID = 1L;

}
