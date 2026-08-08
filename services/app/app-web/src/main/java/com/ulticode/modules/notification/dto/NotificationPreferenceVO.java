package com.ulticode.modules.notification.dto;

import lombok.Data;

/**
 * View object for notification preferences.
 *
 * <p>{@code systemEnabled} maps to the {@code system_enabled} column
 * (renamed from {@code system} via {@code V20260611120000}).
 */
@Data
public class NotificationPreferenceVO {
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean systemEnabled;

    public NotificationPreferenceVO() {
    }

    public NotificationPreferenceVO(Boolean communication, Boolean marketing,
                                    Boolean security, Boolean systemEnabled) {
        this.communication = communication;
        this.marketing = marketing;
        this.security = security;
        this.systemEnabled = systemEnabled;
    }
}
