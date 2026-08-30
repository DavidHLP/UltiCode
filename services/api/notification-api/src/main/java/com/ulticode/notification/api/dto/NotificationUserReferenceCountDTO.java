package com.ulticode.notification.api.dto;

import java.io.Serializable;

/** Bounded count of Notification rows grouped by referenced account id. */
public record NotificationUserReferenceCountDTO(
        String accountId,
        long rowCount) implements Serializable {
    private static final long serialVersionUID = 1L;
}
