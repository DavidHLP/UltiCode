package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * Combined view of every settings category. Returned by
 * {@code GET /admin/settings/all} and {@code POST /admin/settings/reset}.
 *
 * <p>This is a flat projection; it is not persisted directly. The persistence
 * layer stores each category as a separate row in the {@code system_settings}
 * table.
 */
@Data
public class AllSettingsVO {

    // General
    private boolean maintenanceMode;
    private String maintenanceMessage;
    private boolean enableRegistrations;
    private String siteName;
    private String siteDescription;
    private boolean requireEmailVerification;

    // Email (smtpPassword is masked in GET responses)
    private String smtpHost;
    private String smtpPort;
    private String smtpUser;
    private String smtpPassword;
    private String smtpFrom;
    private String smtpFromName;
    private boolean smtpSecure;

    // Rate Limits
    private String rateLimitApi;
    private String rateLimitSubmission;
    private String rateLimitAuth;
    private String rateLimitUpload;

    // Uploads
    private String uploadMaxSize;
    private String uploadAllowedTypes;
    private String uploadMaxFiles;

    // Features
    private boolean featureContest;
    private boolean featureForum;
    private boolean featureSolutions;
    private boolean featureSubscriptions;
    private boolean featureAchievements;
    private boolean featureNotifications;
    private boolean featureBookmarks;
    private boolean featureProblemLists;
}
