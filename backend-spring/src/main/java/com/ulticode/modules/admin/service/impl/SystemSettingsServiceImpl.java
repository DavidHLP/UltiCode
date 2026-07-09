package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.dto.settings.AllSettingsVO;
import com.ulticode.modules.admin.dto.settings.EmailSettingsVO;
import com.ulticode.modules.admin.dto.settings.FeatureTogglesVO;
import com.ulticode.modules.admin.dto.settings.GeneralSettingsVO;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeRequest;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeVO;
import com.ulticode.modules.admin.dto.settings.RateLimitSettingsVO;
import com.ulticode.modules.admin.dto.settings.UploadSettingsVO;
import com.ulticode.modules.admin.service.SystemSettingsService;
import com.ulticode.modules.admin.store.SystemSettingsStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Default {@link SystemSettingsService} &mdash; business policy over the
 * {@link SystemSettingsStore} JSON storage seam.
 *
 * <p>What this service owns (the <em>policy</em>):
 * <ul>
 *   <li>DDL defaults for the five categories (the store accepts a factory).</li>
 *   <li>SMTP password masking on GET, and the "preserve-on-mask" rule on
 *       PATCH so a re-displayed form cannot wipe the secret.</li>
 *   <li>The "accidental empty PATCH" safety guard for feature toggles
 *       (all 8 flags at the JSON-default state).</li>
 *   <li>Maintenance-mode is a single source of truth (lives in the
 *       {@code general} row, not a separate flag).</li>
 *   <li>Audit anchor (which admin took the action) for the destructive
 *       reset path.</li>
 * </ul>
 *
 * <p>What this service deliberately <em>does not</em> own anymore:
 * <ul>
 *   <li>JSON encode/decode of the {@code value} column &mdash; the store.</li>
 *   <li>The five category keys &mdash; the store's
 *       {@link SystemSettingsStore#categoryKeys()}.</li>
 *   <li>The batched read used by {@code GET /admin/settings/all} &mdash; the
 *       store's {@link SystemSettingsStore#loadAllRaw(java.util.Collection)}.</li>
 *   <li>Any direct read of {@code SecurityContextHolder} &mdash; the
 *       {@link CurrentUserProvider} port. Closes the leak that survived
 *       the 2026-07-08 review's {@code CurrentUserProvider} extraction.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private static final String DEFAULT_SITE_NAME = "UltiCode";
    private static final String DEFAULT_SITE_DESCRIPTION = "Online Programming Platform";
    private static final String DEFAULT_SMTP_FROM_NAME = "UltiCode";

    private final SystemSettingsStore store;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    // ===== read paths =====

    @Override
    public GeneralSettingsVO getGeneralSettings() {
        return store.loadOrDefault(SystemSettingsStore.KEY_GENERAL,
                GeneralSettingsVO.class, SystemSettingsServiceImpl::defaultGeneral);
    }

    @Override
    public EmailSettingsVO getEmailSettings() {
        EmailSettingsVO vo = loadEmailUnmasked();
        if (vo.getSmtpPassword() != null && !vo.getSmtpPassword().isEmpty()) {
            vo.setSmtpPassword(EmailSettingsVO.PASSWORD_MASK);
        }
        return vo;
    }

    @Override
    public RateLimitSettingsVO getRateLimitSettings() {
        return store.loadOrDefault(SystemSettingsStore.KEY_RATE_LIMITS,
                RateLimitSettingsVO.class, SystemSettingsServiceImpl::defaultRateLimit);
    }

    @Override
    public UploadSettingsVO getUploadSettings() {
        return store.loadOrDefault(SystemSettingsStore.KEY_UPLOADS,
                UploadSettingsVO.class, SystemSettingsServiceImpl::defaultUpload);
    }

    @Override
    public FeatureTogglesVO getFeatureToggles() {
        return store.loadOrDefault(SystemSettingsStore.KEY_FEATURES,
                FeatureTogglesVO.class, SystemSettingsServiceImpl::defaultFeatures);
    }

    @Override
    public AllSettingsVO getAllSettings() {
        // One batched read of the five category rows.
        Map<String, String> jsonByKey = store.loadAllRaw(store.categoryKeys());

        GeneralSettingsVO g = store.parseOrDefault(jsonByKey.get(SystemSettingsStore.KEY_GENERAL),
                GeneralSettingsVO.class, SystemSettingsServiceImpl::defaultGeneral);
        EmailSettingsVO eRaw = store.parseOrDefault(jsonByKey.get(SystemSettingsStore.KEY_EMAIL),
                EmailSettingsVO.class, SystemSettingsServiceImpl::defaultEmail);
        if (eRaw.getSmtpPassword() != null && !eRaw.getSmtpPassword().isEmpty()) {
            eRaw.setSmtpPassword(EmailSettingsVO.PASSWORD_MASK);
        }
        RateLimitSettingsVO r = store.parseOrDefault(jsonByKey.get(SystemSettingsStore.KEY_RATE_LIMITS),
                RateLimitSettingsVO.class, SystemSettingsServiceImpl::defaultRateLimit);
        UploadSettingsVO u = store.parseOrDefault(jsonByKey.get(SystemSettingsStore.KEY_UPLOADS),
                UploadSettingsVO.class, SystemSettingsServiceImpl::defaultUpload);
        FeatureTogglesVO f = store.parseOrDefault(jsonByKey.get(SystemSettingsStore.KEY_FEATURES),
                FeatureTogglesVO.class, SystemSettingsServiceImpl::defaultFeatures);

        AllSettingsVO all = new AllSettingsVO();
        // General
        all.setMaintenanceMode(g.isMaintenanceMode());
        all.setMaintenanceMessage(g.getMaintenanceMessage());
        all.setEnableRegistrations(g.isEnableRegistrations());
        all.setSiteName(g.getSiteName());
        all.setSiteDescription(g.getSiteDescription());
        all.setRequireEmailVerification(g.isRequireEmailVerification());

        // Email (already masked)
        all.setSmtpHost(eRaw.getSmtpHost());
        all.setSmtpPort(eRaw.getSmtpPort());
        all.setSmtpUser(eRaw.getSmtpUser());
        all.setSmtpPassword(eRaw.getSmtpPassword());
        all.setSmtpFrom(eRaw.getSmtpFrom());
        all.setSmtpFromName(eRaw.getSmtpFromName());
        all.setSmtpSecure(eRaw.isSmtpSecure());

        // Rate Limits
        all.setRateLimitApi(r.getRateLimitApi());
        all.setRateLimitSubmission(r.getRateLimitSubmission());
        all.setRateLimitAuth(r.getRateLimitAuth());
        all.setRateLimitUpload(r.getRateLimitUpload());

        // Uploads
        all.setUploadMaxSize(u.getUploadMaxSize());
        all.setUploadAllowedTypes(u.getUploadAllowedTypes());
        all.setUploadMaxFiles(u.getUploadMaxFiles());

        // Features
        all.setFeatureContest(f.isFeatureContest());
        all.setFeatureForum(f.isFeatureForum());
        all.setFeatureSolutions(f.isFeatureSolutions());
        all.setFeatureSubscriptions(f.isFeatureSubscriptions());
        all.setFeatureAchievements(f.isFeatureAchievements());
        all.setFeatureNotifications(f.isFeatureNotifications());
        all.setFeatureBookmarks(f.isFeatureBookmarks());
        all.setFeatureProblemLists(f.isFeatureProblemLists());

        return all;
    }

    // ===== write paths =====

    @Override
    public GeneralSettingsVO updateGeneralSettings(GeneralSettingsVO vo) {
        store.save(SystemSettingsStore.KEY_GENERAL, vo);
        return getGeneralSettings();
    }

    @Override
    public EmailSettingsVO updateEmailSettings(EmailSettingsVO vo) {
        // If the client posts the mask (or omits the field), preserve the
        // currently stored password so a re-displayed form cannot wipe it.
        String posted = vo.getSmtpPassword();
        if (posted == null || EmailSettingsVO.PASSWORD_MASK.equals(posted)) {
            EmailSettingsVO existing = loadEmailUnmasked();
            vo.setSmtpPassword(existing.getSmtpPassword());
        }
        store.save(SystemSettingsStore.KEY_EMAIL, vo);
        return getEmailSettings();
    }

    @Override
    public RateLimitSettingsVO updateRateLimitSettings(RateLimitSettingsVO vo) {
        store.save(SystemSettingsStore.KEY_RATE_LIMITS, vo);
        return getRateLimitSettings();
    }

    @Override
    public UploadSettingsVO updateUploadSettings(UploadSettingsVO vo) {
        store.save(SystemSettingsStore.KEY_UPLOADS, vo);
        return getUploadSettings();
    }

    @Override
    public FeatureTogglesVO updateFeatureToggles(FeatureTogglesVO vo) {
        // M1 (review): reject the "accidental empty PATCH" case where a
        // malformed frontend posts `{}` and silently disables every feature.
        // Eight false flags is the exact JSON-default state; if the admin
        // really wants all features off, they can call
        // POST /admin/settings/maintenance to put the site into maintenance
        // mode, which is the supported mechanism for "take the platform
        // offline". Disabling every feature flag is not a supported
        // operation and would brick the public UI.
        if (isAllDefaults(vo)) {
            log.warn("Rejected PATCH /admin/settings/features with all defaults; "
                    + "this is almost always a frontend bug, not a real intent.");
            throw new BusinessException(ErrorCode.SETTING_INVALID_VALUE,
                    "Refusing to disable all 8 feature flags in a single PATCH; "
                            + "this is treated as an accidental empty request. "
                            + "If you really want to take the platform offline, "
                            + "use POST /admin/settings/maintenance instead.");
        }
        store.save(SystemSettingsStore.KEY_FEATURES, vo);
        return getFeatureToggles();
    }

    @Override
    public AllSettingsVO resetToDefaults() {
        // L3 (review): include the acting admin's identifier in the audit log
        // so destructive resets are traceable after the fact. The
        // CurrentUserProvider port is the only seam that should read the
        // security context (SecurityCurrentUserProvider is the sole adapter
        // that touches SecurityContextHolder).
        String actor = currentUserProvider.getCurrentUserId();
        log.warn("AUDIT resetToDefaults: actor={} action=delete_all_settings rows={}",
                actor, store.categoryKeys());
        store.deleteAll(store.categoryKeys());
        return getAllSettings();
    }

    @Override
    public MaintenanceModeVO toggleMaintenance(MaintenanceModeRequest request) {
        GeneralSettingsVO general = getGeneralSettings();
        general.setMaintenanceMode(request.getEnabled());
        general.setMaintenanceMessage(request.getMessage());
        store.save(SystemSettingsStore.KEY_GENERAL, general);

        log.info("AUDIT toggleMaintenance: actor={} enabled={} message={}",
                currentUserProvider.getCurrentUserId(), request.getEnabled(), request.getMessage());

        MaintenanceModeVO vo = new MaintenanceModeVO();
        vo.setMaintenanceMode(request.getEnabled());
        vo.setMessage(request.getMessage());
        return vo;
    }

    @Override
    public Map<String, Object> clearCache() {
        // No Redis cache is in use for settings today; this is a no-op
        // reserved for future invalidation hooks. Returned shape lets
        // the frontend confirm the operation scope.
        log.info("Clearing system settings cache (no-op, no cache configured)");
        return Map.of(
                "clearedScopes", List.of("settings"),
                "timestamp", LocalDateTime.now(clock).toString()
        );
    }

    // ===== policy helpers =====

    /**
     * Read the raw email row, bypassing the password mask used by
     * {@link #getEmailSettings()}. Only the update path needs the
     * cleartext value.
     */
    private EmailSettingsVO loadEmailUnmasked() {
        return store.loadOrDefault(SystemSettingsStore.KEY_EMAIL,
                EmailSettingsVO.class, SystemSettingsServiceImpl::defaultEmail);
    }

    /**
     * Detect the "all 8 feature flags at JSON default (false)" state, which
     * is exactly what Jackson produces from an empty {@code {}}. See
     * {@link #updateFeatureToggles(FeatureTogglesVO)} for the rationale.
     */
    private static boolean isAllDefaults(FeatureTogglesVO v) {
        return !v.isFeatureContest()
                && !v.isFeatureForum()
                && !v.isFeatureSolutions()
                && !v.isFeatureSubscriptions()
                && !v.isFeatureAchievements()
                && !v.isFeatureNotifications()
                && !v.isFeatureBookmarks()
                && !v.isFeatureProblemLists();
    }

    // ===== DDL defaults (policy, not storage shape) =====

    private static GeneralSettingsVO defaultGeneral() {
        GeneralSettingsVO v = new GeneralSettingsVO();
        v.setMaintenanceMode(false);
        v.setMaintenanceMessage("");
        v.setEnableRegistrations(true);
        v.setSiteName(DEFAULT_SITE_NAME);
        v.setSiteDescription(DEFAULT_SITE_DESCRIPTION);
        v.setRequireEmailVerification(false);
        return v;
    }

    private static EmailSettingsVO defaultEmail() {
        EmailSettingsVO v = new EmailSettingsVO();
        v.setSmtpHost("");
        v.setSmtpPort("587");
        v.setSmtpUser("");
        v.setSmtpPassword("");
        v.setSmtpFrom("");
        v.setSmtpFromName(DEFAULT_SMTP_FROM_NAME);
        v.setSmtpSecure(false);
        return v;
    }

    private static RateLimitSettingsVO defaultRateLimit() {
        RateLimitSettingsVO v = new RateLimitSettingsVO();
        v.setRateLimitApi("100");
        v.setRateLimitSubmission("10");
        v.setRateLimitAuth("5");
        v.setRateLimitUpload("20");
        return v;
    }

    private static UploadSettingsVO defaultUpload() {
        UploadSettingsVO v = new UploadSettingsVO();
        v.setUploadMaxSize("10MB");
        v.setUploadAllowedTypes("jpg,jpeg,png,gif,pdf,zip");
        v.setUploadMaxFiles("5");
        return v;
    }

    private static FeatureTogglesVO defaultFeatures() {
        FeatureTogglesVO v = new FeatureTogglesVO();
        v.setFeatureContest(true);
        v.setFeatureForum(true);
        v.setFeatureSolutions(true);
        v.setFeatureSubscriptions(true);
        v.setFeatureAchievements(true);
        v.setFeatureNotifications(true);
        v.setFeatureBookmarks(true);
        v.setFeatureProblemLists(true);
        return v;
    }
}
