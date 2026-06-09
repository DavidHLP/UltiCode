package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * General system settings.
 *
 * <p>Aggregates site-level configuration and the maintenance mode flag, which
 * is the single source of truth for the platform's maintenance state.
 */
@Data
public class GeneralSettingsVO {

    /** Whether the site is currently in maintenance mode. */
    private boolean maintenanceMode;

    /** Message displayed to users when the site is in maintenance mode. */
    private String maintenanceMessage;

    /** Whether new user registrations are allowed. */
    private boolean enableRegistrations;

    /** Display name of the site. */
    private String siteName;

    /** Short description shown on landing pages and meta tags. */
    private String siteDescription;

    /** Whether email verification is required to activate a new account. */
    private boolean requireEmailVerification;
}
