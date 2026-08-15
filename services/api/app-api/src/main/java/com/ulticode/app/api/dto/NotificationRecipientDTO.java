package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Minimum recipient projection required by notification delivery.
 *
 * <p>Email and governance flags are returned through the App-owned
 * notification read seam; no Auth entity or credential field crosses the
 * contract.
 */
public record NotificationRecipientDTO(
        String userId,
        String email,
        boolean active,
        boolean banned) implements Serializable {
}
