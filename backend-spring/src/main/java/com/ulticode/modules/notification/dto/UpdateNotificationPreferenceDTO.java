package com.ulticode.modules.notification.dto;

import lombok.Data;

/**
 * DTO for updating notification preferences.
 */
@Data
public class UpdateNotificationPreferenceDTO {
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean system;
}
