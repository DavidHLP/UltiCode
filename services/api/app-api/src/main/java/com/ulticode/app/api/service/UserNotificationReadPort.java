package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.NotificationRecipientDTO;

import java.util.Collection;
import java.util.List;

/**
 * Focused recipient read seam consumed by the notification owner.
 *
 * <p>The Notification adapter queries Auth directly and maps its minimum
 * account projection into this contract. Notification preferences are
 * intentionally not part of this port; they remain local to the notification
 * owner and are evaluated by its dispatcher.
 */
public interface UserNotificationReadPort {

    /**
     * Read one recipient, or {@code null} when the account is unknown.
     */
    NotificationRecipientDTO findById(String userId);

    /**
     * Read known recipients. The result is never {@code null} and may omit
     * unknown account ids.
     */
    List<NotificationRecipientDTO> findByIds(Collection<String> userIds);

    /**
     * Return Auth-authoritative active, non-banned, non-deleted recipients.
     */
    List<String> findAllActiveIds();
}
