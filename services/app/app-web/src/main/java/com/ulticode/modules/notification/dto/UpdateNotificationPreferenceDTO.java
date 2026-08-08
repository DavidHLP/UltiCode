package com.ulticode.modules.notification.dto;

import lombok.Data;

/**
 * DTO for updating notification preferences.
 *
 * <p>{@code systemEnabled} maps to the {@code system_enabled} column
 * (renamed from {@code system} via {@code V20260611120000}).
 */
@Data
public class UpdateNotificationPreferenceDTO {
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean systemEnabled;
}
