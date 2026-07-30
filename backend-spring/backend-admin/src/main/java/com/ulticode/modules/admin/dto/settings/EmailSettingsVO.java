package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * Email / SMTP settings.
 *
 * <p>The {@code smtpPassword} field is masked in GET responses (replaced with
 * {@link #PASSWORD_MASK}) and only updated when the client posts a non-mask
 * value, so frontends can re-display the form without overwriting the secret.
 */
@Data
public class EmailSettingsVO {

    /** Sentinel value returned in place of a real SMTP password on GET. */
    public static final String PASSWORD_MASK = "***";

    private String smtpHost;
    private String smtpPort;
    private String smtpUser;
    private String smtpPassword;
    private String smtpFrom;
    private String smtpFromName;
    private boolean smtpSecure;
}
