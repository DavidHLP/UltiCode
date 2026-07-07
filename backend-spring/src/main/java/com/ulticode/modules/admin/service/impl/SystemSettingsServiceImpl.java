package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ulticode.modules.admin.entity.SystemSetting;
import com.ulticode.modules.admin.mapper.SystemSettingMapper;
import com.ulticode.modules.admin.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Default {@link SystemSettingsService} backed by the {@code system_settings}
 * table. Each settings category is stored as a single row whose {@code value}
 * column holds the JSON-serialized view object.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private static final String KEY_GENERAL = "general";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_RATE_LIMITS = "rate-limits";
    private static final String KEY_UPLOADS = "uploads";
    private static final String KEY_FEATURES = "features";

    private static final List<String> ALL_KEYS =
            List.of(KEY_GENERAL, KEY_EMAIL, KEY_RATE_LIMITS, KEY_UPLOADS, KEY_FEATURES);

    private static final String DEFAULT_SITE_NAME = "UltiCode";
    private static final String DEFAULT_SITE_DESCRIPTION = "Online Programming Platform";
    private static final String DEFAULT_SMTP_FROM_NAME = "UltiCode";

    private final SystemSettingMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ===== read paths =====

    @Override
    public GeneralSettingsVO getGeneralSettings() {
        return loadVo(KEY_GENERAL, GeneralSettingsVO.class, SystemSettingsServiceImpl::defaultGeneral);
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
        return loadVo(KEY_RATE_LIMITS, RateLimitSettingsVO.class, SystemSettingsServiceImpl::defaultRateLimit);
    }

    @Override
    public UploadSettingsVO getUploadSettings() {
        return loadVo(KEY_UPLOADS, UploadSettingsVO.class, SystemSettingsServiceImpl::defaultUpload);
    }

    @Override
    public FeatureTogglesVO getFeatureToggles() {
        return loadVo(KEY_FEATURES, FeatureTogglesVO.class, SystemSettingsServiceImpl::defaultFeatures);
    }

    @Override
    public AllSettingsVO getAllSettings() {
        // L4 (review): single batched query (selectBatchIds) instead of 5
        // sequential selectById calls.
        Map<String, String> jsonByKey = loadAllRows();

        GeneralSettingsVO g = parseOr(jsonByKey.get(KEY_GENERAL),
                GeneralSettingsVO.class, SystemSettingsServiceImpl::defaultGeneral);
        EmailSettingsVO eRaw = parseOr(jsonByKey.get(KEY_EMAIL),
                EmailSettingsVO.class, SystemSettingsServiceImpl::defaultEmail);
        if (eRaw.getSmtpPassword() != null && !eRaw.getSmtpPassword().isEmpty()) {
            eRaw.setSmtpPassword(EmailSettingsVO.PASSWORD_MASK);
        }
        RateLimitSettingsVO r = parseOr(jsonByKey.get(KEY_RATE_LIMITS),
                RateLimitSettingsVO.class, SystemSettingsServiceImpl::defaultRateLimit);
        UploadSettingsVO u = parseOr(jsonByKey.get(KEY_UPLOADS),
                UploadSettingsVO.class, SystemSettingsServiceImpl::defaultUpload);
        FeatureTogglesVO f = parseOr(jsonByKey.get(KEY_FEATURES),
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
        saveVo(KEY_GENERAL, vo);
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
        saveVo(KEY_EMAIL, vo);
        return getEmailSettings();
    }

    @Override
    public RateLimitSettingsVO updateRateLimitSettings(RateLimitSettingsVO vo) {
        saveVo(KEY_RATE_LIMITS, vo);
        return getRateLimitSettings();
    }

    @Override
    public UploadSettingsVO updateUploadSettings(UploadSettingsVO vo) {
        saveVo(KEY_UPLOADS, vo);
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
        saveVo(KEY_FEATURES, vo);
        return getFeatureToggles();
    }

    @Override
    public AllSettingsVO resetToDefaults() {
        // L3 (review): include the acting admin's identifier in the audit log
        // so destructive resets are traceable after the fact.
        String actor = currentActor();
        log.warn("AUDIT resetToDefaults: actor={} action=delete_all_settings rows={}",
                actor, ALL_KEYS);
        for (String key : ALL_KEYS) {
            mapper.deleteById(key);
        }
        return getAllSettings();
    }

    @Override
    public MaintenanceModeVO toggleMaintenance(MaintenanceModeRequest request) {
        // L2 (review): the null check on `request` and `request.getEnabled()`
        // was unreachable — @NotNull on `Boolean enabled` plus Spring's
        // request-body binding already reject null with HTTP 400 before this
        // method runs. Rely on @Valid + the global handler chain.
        GeneralSettingsVO general = getGeneralSettings();
        general.setMaintenanceMode(request.getEnabled());
        general.setMaintenanceMessage(request.getMessage());
        saveVo(KEY_GENERAL, general);

        log.info("AUDIT toggleMaintenance: actor={} enabled={} message={}",
                currentActor(), request.getEnabled(), request.getMessage());

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

    // ===== persistence helpers =====

    /**
     * Read the raw email row, bypassing the password mask used by
     * {@link #getEmailSettings()}. Only the update path needs the
     * cleartext value.
     */
    private EmailSettingsVO loadEmailUnmasked() {
        return loadVo(KEY_EMAIL, EmailSettingsVO.class, SystemSettingsServiceImpl::defaultEmail);
    }

    /**
     * Load a category row, falling back to {@code defaultFactory} when the
     * row is absent. The default is not persisted; it is returned as-is so
     * the very first GET is observable.
     */
    private <T> T loadVo(String key, Class<T> type, Supplier<T> defaultFactory) {
        SystemSetting row = mapper.selectById(key);
        if (row == null || row.getValue() == null || row.getValue().isBlank()) {
            return defaultFactory.get();
        }
        return parseOr(row.getValue(), type, defaultFactory);
    }

    /**
     * Parse a JSON payload to {@code type}, or return the default if the
     * payload is missing/blank or unparseable. Used by both the single-row
     * read path and the batched read path in {@link #getAllSettings()}.
     */
    private <T> T parseOr(String json, Class<T> type, Supplier<T> defaultFactory) {
        if (json == null || json.isBlank()) {
            return defaultFactory.get();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize {} payload, returning default", type.getSimpleName(), e);
            return defaultFactory.get();
        }
    }

    /**
     * Batched read used by {@link #getAllSettings()} (L4 perf optimization):
     * one {@code SELECT ... WHERE `key` IN (...)} instead of 5 round-trips.
     * Returns a key→JSON map; absent rows are simply missing from the map.
     */
    private Map<String, String> loadAllRows() {
        List<SystemSetting> rows = mapper.selectBatchIds(ALL_KEYS);
        Map<String, String> result = new HashMap<>(rows.size());
        for (SystemSetting row : rows) {
            if (row != null && row.getKey() != null) {
                result.put(row.getKey(), row.getValue());
            }
        }
        return result;
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

    /**
     * Best-effort current-user identifier for audit logs. Pulls the
     * Spring Security {@code Authentication} from the request thread's
     * context, falling back to {@code "anonymous"} when none is bound
     * (e.g. background jobs that should not normally reach this code).
     */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null || auth.getName() == null) ? "anonymous" : auth.getName();
    }

    /** Serialize {@code vo} to JSON and upsert the row identified by {@code key}. */
    private void saveVo(String key, Object vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            SystemSetting row = new SystemSetting();
            row.setKey(key);
            row.setValue(json);
            row.setUpdatedAt(LocalDateTime.now(clock));
            mapper.insertOrUpdate(row);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize setting key={}", key, e);
            throw new BusinessException(ErrorCode.SETTING_PERSISTENCE_FAILED);
        }
    }

    // ===== defaults =====

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

    // ===== unused helpers (kept for future use) =====

    /** Available for future queries that need to filter or count. */
    @SuppressWarnings("unused")
    private long countAll() {
        return mapper.selectCount(Wrappers.emptyWrapper());
    }
}
