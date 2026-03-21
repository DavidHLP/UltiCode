package com.ulticode.modules.notification.dto;

import lombok.Data;

/**
 * View object for notification preferences.
 */
@Data
public class NotificationPreferenceVO {
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean system;
}
