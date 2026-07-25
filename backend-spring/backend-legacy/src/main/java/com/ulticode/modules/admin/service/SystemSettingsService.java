package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.ClearCacheResponseVO;
import com.ulticode.modules.admin.dto.settings.AllSettingsVO;
import com.ulticode.modules.admin.dto.settings.EmailSettingsVO;
import com.ulticode.modules.admin.dto.settings.FeatureTogglesVO;
import com.ulticode.modules.admin.dto.settings.GeneralSettingsVO;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeRequest;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeVO;
import com.ulticode.modules.admin.dto.settings.RateLimitSettingsVO;
import com.ulticode.modules.admin.dto.settings.UploadSettingsVO;

/**
 * Admin-facing system settings service.
 *
 * <p>Settings are persisted in the {@code system_settings} table as
 * JSON-serialized values, one row per category ({@code general},
 * {@code email}, {@code rate-limits}, {@code uploads}, {@code features}).
 *
 * <p>Read methods return defaults when a category has never been written.
 * Write methods upsert; {@link #resetToDefaults()} deletes all rows so
 * defaults are re-seeded on the next read.
 */
public interface SystemSettingsService {

    GeneralSettingsVO getGeneralSettings();

    GeneralSettingsVO updateGeneralSettings(GeneralSettingsVO vo);

    EmailSettingsVO getEmailSettings();

    EmailSettingsVO updateEmailSettings(EmailSettingsVO vo);

    RateLimitSettingsVO getRateLimitSettings();

    RateLimitSettingsVO updateRateLimitSettings(RateLimitSettingsVO vo);

    UploadSettingsVO getUploadSettings();

    UploadSettingsVO updateUploadSettings(UploadSettingsVO vo);

    FeatureTogglesVO getFeatureToggles();

    FeatureTogglesVO updateFeatureToggles(FeatureTogglesVO vo);

    AllSettingsVO getAllSettings();

    AllSettingsVO resetToDefaults();

    MaintenanceModeVO toggleMaintenance(MaintenanceModeRequest request);

    /** Placeholder for future cache invalidation. */
    ClearCacheResponseVO clearCache();
}
