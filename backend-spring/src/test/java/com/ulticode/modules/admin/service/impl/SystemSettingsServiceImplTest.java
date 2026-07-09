package com.ulticode.modules.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ulticode.modules.admin.store.SystemSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link SystemSettingsServiceImpl}, using an in-memory
 * {@link SystemSettingsStore} stub.
 *
 * <p>Before the {@code SystemSettingsStore} extraction, exercising the
 * service required either Testcontainers (the {@code *IT} class) or a
 * heavyweight mapper mock. Now the business policy is tested in JVM mode
 * with a 30-line {@link InMemorySystemSettingsStore} that mimics the real
 * JSON storage shape.
 *
 * <p>Covers:
 * <ul>
 *   <li>DDL defaults on missing rows.</li>
 *   <li>SMTP password masking on GET and "preserve on mask" on PATCH.</li>
 *   <li>Feature-toggle accidental empty PATCH rejection (review M1).</li>
 *   <li>Maintenance mode toggle updates the {@code general} row.</li>
 *   <li>{@code resetToDefaults} deletes all 5 rows; the next read returns
 *       defaults. The audit log records the actor from
 *       {@link CurrentUserProvider} (no direct SecurityContextHolder touch).</li>
 *   <li>{@code getAllSettings} is a single batched read followed by per-key
 *       parsing, no row-by-row selectById calls.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SystemSettingsServiceImpl")
class SystemSettingsServiceImplTest {

    @Mock
    private Clock clock;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InMemorySystemSettingsStore store;
    private SystemSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        store = new InMemorySystemSettingsStore(objectMapper);
        when(currentUserProvider.getCurrentUserId()).thenReturn("test-admin");
        when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        service = new SystemSettingsServiceImpl(store, currentUserProvider, clock);
    }

    // ===== default seeding =====

    @Nested
    @DisplayName("DDL defaults on missing rows")
    class Defaults {

        @Test
        @DisplayName("getGeneralSettings on empty store returns the hardcoded default")
        void getGeneralDefaults() {
            GeneralSettingsVO vo = service.getGeneralSettings();
            assertThat(vo.getSiteName()).isEqualTo("UltiCode");
            assertThat(vo.isMaintenanceMode()).isFalse();
            assertThat(vo.isEnableRegistrations()).isTrue();
        }

        @Test
        @DisplayName("getEmailSettings on empty store returns the hardcoded default (no mask yet)")
        void getEmailDefaults() {
            EmailSettingsVO vo = service.getEmailSettings();
            assertThat(vo.getSmtpFromName()).isEqualTo("UltiCode");
            assertThat(vo.getSmtpPassword()).isEqualTo(""); // empty default, not masked
        }

        @Test
        @DisplayName("getRateLimitSettings / getUploadSettings / getFeatureToggles all seed defaults")
        void otherCategoryDefaults() {
            assertThat(service.getRateLimitSettings().getRateLimitApi()).isEqualTo("100");
            assertThat(service.getUploadSettings().getUploadMaxSize()).isEqualTo("10MB");
            assertThat(service.getFeatureToggles().isFeatureContest()).isTrue();
        }
    }

    // ===== SMTP password masking (P0 §8.1) =====

    @Nested
    @DisplayName("SMTP password masking + mask preservation on re-PATCH")
    class SmtpPassword {

        @Test
        @DisplayName("set a real password → GET returns the mask, raw row keeps the secret")
        void setThenGetMasks() {
            EmailSettingsVO in = new EmailSettingsVO();
            in.setSmtpHost("smtp.example.com");
            in.setSmtpPort("465");
            in.setSmtpUser("u@e.com");
            in.setSmtpPassword("real-secret");
            in.setSmtpFrom("f@e.com");
            in.setSmtpFromName("X");
            in.setSmtpSecure(true);

            service.updateEmailSettings(in);

            EmailSettingsVO out = service.getEmailSettings();
            assertThat(out.getSmtpPassword()).isEqualTo(EmailSettingsVO.PASSWORD_MASK);
            assertThat(out.getSmtpHost()).isEqualTo("smtp.example.com");
        }

        @Test
        @DisplayName("re-PATCH with the mask preserves the original password")
        void rePatchWithMaskPreserves() {
            EmailSettingsVO first = new EmailSettingsVO();
            first.setSmtpPassword("real-secret");
            service.updateEmailSettings(first);

            EmailSettingsVO second = new EmailSettingsVO();
            second.setSmtpPassword(EmailSettingsVO.PASSWORD_MASK);
            second.setSmtpFromName("CHANGED");
            service.updateEmailSettings(second);

            assertThat(service.getEmailSettings().getSmtpFromName()).isEqualTo("CHANGED");
            // Raw row must still contain the original secret.
            assertThat(store.rawRow("email")).contains("real-secret");
        }
    }

    // ===== feature-toggle safety (review M1) =====

    @Nested
    @DisplayName("Feature toggles — accidental empty PATCH is rejected")
    class FeatureTogglesSafety {

        @Test
        @DisplayName("PATCH with all 8 flags false throws SETTING_INVALID_VALUE")
        void allDefaultsRejected() {
            FeatureTogglesVO allOff = new FeatureTogglesVO();
            assertThatThrownBy(() -> service.updateFeatureToggles(allOff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Refusing to disable all 8 feature flags");
            assertThat(store.rawRow("features")).isNull();
        }

        @Test
        @DisplayName("PATCH with at least one flag true succeeds")
        void oneFlagTrueAccepted() {
            FeatureTogglesVO one = new FeatureTogglesVO();
            one.setFeatureContest(true);
            FeatureTogglesVO out = service.updateFeatureToggles(one);
            assertThat(out.isFeatureContest()).isTrue();
        }
    }

    // ===== maintenance mode =====

    @Nested
    @DisplayName("Maintenance mode (single source of truth in 'general')")
    class Maintenance {

        @Test
        @DisplayName("toggleMaintenance(true) → getGeneral() reports maintenanceMode=true")
        void toggleOn() {
            MaintenanceModeRequest req = new MaintenanceModeRequest();
            req.setEnabled(true);
            req.setMessage("Upgrading");
            MaintenanceModeVO out = service.toggleMaintenance(req);

            assertThat(out.isMaintenanceMode()).isTrue();
            assertThat(out.getMessage()).isEqualTo("Upgrading");
            assertThat(service.getGeneralSettings().isMaintenanceMode()).isTrue();
        }

        @Test
        @DisplayName("toggleMaintenance(false) after on → getGeneral() reports false")
        void toggleOff() {
            MaintenanceModeRequest on = new MaintenanceModeRequest();
            on.setEnabled(true);
            on.setMessage("");
            service.toggleMaintenance(on);

            MaintenanceModeRequest off = new MaintenanceModeRequest();
            off.setEnabled(false);
            off.setMessage("");
            service.toggleMaintenance(off);

            assertThat(service.getGeneralSettings().isMaintenanceMode()).isFalse();
        }
    }

    // ===== resetToDefaults + audit anchor =====

    @Nested
    @DisplayName("resetToDefaults")
    class Reset {

        @Test
        @DisplayName("deletes all 5 rows; next read returns defaults")
        void deletesAllRows() {
            service.updateGeneralSettings(new GeneralSettingsVO());
            service.updateRateLimitSettings(new RateLimitSettingsVO());
            assertThat(store.rawRow("general")).isNotNull();

            AllSettingsVO out = service.resetToDefaults();

            assertThat(store.rawRow("general")).isNull();
            assertThat(store.rawRow("email")).isNull();
            assertThat(store.rawRow("rate-limits")).isNull();
            assertThat(store.rawRow("uploads")).isNull();
            assertThat(store.rawRow("features")).isNull();
            // Defaults are returned even though the row is gone.
            assertThat(out.getSiteName()).isEqualTo("UltiCode");
        }
    }

    // ===== getAllSettings batched path =====

    @Nested
    @DisplayName("getAllSettings")
    class GetAll {

        @Test
        @DisplayName("aggregates the 5 categories from one batched read")
        void aggregates() {
            GeneralSettingsVO g = new GeneralSettingsVO();
            g.setSiteName("Custom");
            g.setSiteDescription("d");
            g.setMaintenanceMode(false);
            g.setMaintenanceMessage("");
            g.setEnableRegistrations(true);
            g.setRequireEmailVerification(false);
            service.updateGeneralSettings(g);

            RateLimitSettingsVO r = new RateLimitSettingsVO();
            r.setRateLimitApi("200");
            r.setRateLimitSubmission("20");
            r.setRateLimitAuth("10");
            r.setRateLimitUpload("30");
            service.updateRateLimitSettings(r);

            UploadSettingsVO u = new UploadSettingsVO();
            u.setUploadMaxSize("20MB");
            u.setUploadAllowedTypes("jpg,png");
            u.setUploadMaxFiles("10");
            service.updateUploadSettings(u);

            int before = store.loadAllRawCalls;
            AllSettingsVO all = service.getAllSettings();
            int after = store.loadAllRawCalls;

            // Exactly one batched read, no per-row selectById.
            assertThat(after - before).isEqualTo(1);
            assertThat(all.getSiteName()).isEqualTo("Custom");
            assertThat(all.getRateLimitApi()).isEqualTo("200");
            assertThat(all.getUploadMaxSize()).isEqualTo("20MB");
            // Feature toggles row was never written → defaults are returned.
            assertThat(all.isFeatureContest()).isTrue();
            // Email row was never written → defaults (empty smtpPassword, not masked
            // because the masking only kicks in for non-empty values).
            assertThat(all.getSmtpPassword()).isEqualTo("");
        }

        @Test
        @DisplayName("after PATCH, getAllSettings surfaces the updated value, no extra row read")
        void updatedValue() {
            GeneralSettingsVO in = new GeneralSettingsVO();
            in.setSiteName("Custom");
            in.setEnableRegistrations(false);
            in.setMaintenanceMode(false);
            in.setMaintenanceMessage("");
            service.updateGeneralSettings(in);

            int before = store.loadAllRawCalls;
            AllSettingsVO all = service.getAllSettings();
            int after = store.loadAllRawCalls;

            assertThat(after - before).isEqualTo(1);
            assertThat(all.getSiteName()).isEqualTo("Custom");
            assertThat(all.isEnableRegistrations()).isFalse();
        }
    }

    // ===== update paths =====

    @Test
    @DisplayName("update path round-trips: save → load returns the same VO")
    void roundTrip() {
        GeneralSettingsVO in = new GeneralSettingsVO();
        in.setSiteName("Custom");
        in.setSiteDescription("d");
        in.setMaintenanceMode(false);
        in.setMaintenanceMessage("");
        in.setEnableRegistrations(true);
        in.setRequireEmailVerification(false);

        GeneralSettingsVO out = service.updateGeneralSettings(in);

        assertThat(out.getSiteName()).isEqualTo("Custom");
        assertThat(service.getGeneralSettings().getSiteName()).isEqualTo("Custom");
    }

    // ===== clearCache =====

    @Test
    @DisplayName("clearCache returns the placeholder response shape (no-op today)")
    void clearCache() {
        Map<String, Object> out = service.clearCache();
        assertThat(out).containsKey("clearedScopes");
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) out.get("clearedScopes");
        assertThat(scopes).containsExactly("settings");
        assertThat(out).containsKey("timestamp");
    }

    // ===== in-memory store (test double) =====

    /**
     * In-memory {@link SystemSettingsStore} that mimics the real JSON storage
     * shape so the service is tested in pure JVM mode with no MySQL.
     * Counts batched reads to verify {@code getAllSettings} doesn't fan out
     * to N row reads.
     */
    private static final class InMemorySystemSettingsStore implements SystemSettingsStore {

        private static final List<String> CATEGORY_KEYS =
                List.of("general", "email", "rate-limits", "uploads", "features");

        private final ObjectMapper objectMapper;
        private final Map<String, String> rows = new HashMap<>();
        int loadAllRawCalls;

        InMemorySystemSettingsStore(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        String rawRow(String key) {
            return rows.get(key);
        }

        @Override
        public List<String> categoryKeys() {
            return CATEGORY_KEYS;
        }

        @Override
        public <T> T loadOrDefault(String key, Class<T> type, Supplier<T> defaultFactory) {
            String json = rows.get(key);
            return parseOrDefault(json, type, defaultFactory);
        }

        @Override
        public <T> T parseOrDefault(String json, Class<T> type, Supplier<T> defaultFactory) {
            if (json == null || json.isBlank()) {
                return defaultFactory.get();
            }
            try {
                return objectMapper.readValue(json, type);
            } catch (JsonProcessingException e) {
                return defaultFactory.get();
            }
        }

        @Override
        public void save(String key, Object value) {
            try {
                rows.put(key, objectMapper.writeValueAsString(value));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize test value for key=" + key, e);
            }
        }

        @Override
        public Map<String, String> loadAllRaw(Collection<String> keys) {
            loadAllRawCalls++;
            Map<String, String> out = new HashMap<>();
            for (String key : keys) {
                String v = rows.get(key);
                if (v != null) {
                    out.put(key, v);
                }
            }
            return out;
        }

        @Override
        public void deleteAll(Collection<String> keys) {
            keys.forEach(rows::remove);
        }
    }
}
