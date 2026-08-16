package com.ulticode.auth.api.dto;

import java.io.Serializable;

/**
 * Auth-owned minimum recipient projection for notification delivery.
 *
 * <p>Email is intentionally exposed only through this internal, focused
 * contract rather than the generic public identity projection.
 */
public record AuthNotificationRecipientDTO(
        String accountId,
        String email,
        boolean active,
        boolean banned) implements Serializable {
    private static final long serialVersionUID = 1L;

}
