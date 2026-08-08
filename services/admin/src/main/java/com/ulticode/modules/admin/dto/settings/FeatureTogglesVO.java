package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * Per-feature on/off flags controlling which modules are visible to end users.
 */
@Data
public class FeatureTogglesVO {

    private boolean featureContest;
    private boolean featureForum;
    private boolean featureSolutions;
    private boolean featureSubscriptions;
    private boolean featureAchievements;
    private boolean featureNotifications;
    private boolean featureBookmarks;
    private boolean featureProblemLists;
}
