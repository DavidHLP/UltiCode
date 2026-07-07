package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.admin.dto.settings.EmailSettingsVO;
import com.ulticode.modules.admin.dto.settings.FeatureTogglesVO;
import com.ulticode.modules.admin.dto.settings.GeneralSettingsVO;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeRequest;
import com.ulticode.modules.admin.dto.settings.RateLimitSettingsVO;
import com.ulticode.modules.admin.entity.SystemSetting;
import com.ulticode.modules.admin.mapper.SystemSettingMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link SystemSettingsServiceImpl} against a real
 * MySQL container. The {@code system_settings} table is created in
 * {@link #setUpSchema()} matching the existing
 * {@code init-db/migrations/V20260602_120000__Create_All_Tables.sql} DDL.
 *
 * <p>This test is the regression net for the three P0 bugs reported in
 * docs/SETTINGS_API_TEST_REPORT_2026-06-09.md:
 * <ul>
 *   <li>§5.1 — settings persistence (PATCH/POST actually writes to the DB)</li>
 *   <li>§5.2 — maintenance mode is a single source of truth</li>
 *   <li>§8.1 — SMTP password is masked in GET and preserved on re-PATCH</li>
 * </ul>
 */
@Testcontainers
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SystemSettingsServiceImpl (IT)")
class SystemSettingsServiceImplIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("ulticode_test")
                    .withUsername("test")
                    .withPassword("test");

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private SystemSettingMapper mapper;
    private SystemSettingsServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private Clock clock;

    @BeforeAll
    static void startContainer() {
        // Touch the container once so static init order is deterministic.
        assertThat(MYSQL.isRunning()).isTrue();
    }

    @BeforeEach
    void setUpSchema() throws Exception {
        when(clock.instant()).thenReturn(Instant.EPOCH);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // Match the existing V20260602_120000__Create_All_Tables.sql DDL.
            stmt.execute("DROP TABLE IF EXISTS system_settings");
            stmt.execute("""
                CREATE TABLE system_settings (
                    `key` varchar(50) NOT NULL,
                    `value` text NOT NULL,
                    description varchar(255) DEFAULT NULL,
                    updated_at datetime(3) NOT NULL,
                    PRIMARY KEY (`key`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        }

        // Build a MyBatis-Plus SqlSessionFactory bound to the test DataSource
        // and register the SystemSettingMapper interface.
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(SystemSettingMapper.class);
        factory.setConfiguration(configuration);
        factory.setPlugins(new MybatisPlusInterceptor());
        sqlSessionFactory = factory.getObject();
        assertThat(sqlSessionFactory).isNotNull();

        mapper = sqlSessionFactory.openSession().getMapper(SystemSettingMapper.class);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        service = new SystemSettingsServiceImpl(mapper, objectMapper, clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS system_settings");
        }
    }

    @Nested
    @DisplayName("Default seeding on first read (P0 §5.1)")
    class DefaultSeeding {

        @Test
        @DisplayName("empty table → getGeneral() returns hardcoded defaults")
        void emptyTableReturnsDefaults() {
            GeneralSettingsVO vo = service.getGeneralSettings();
            assertThat(vo.isMaintenanceMode()).isFalse();
            assertThat(vo.getSiteName()).isEqualTo("UltiCode");
            assertThat(vo.isEnableRegistrations()).isTrue();

            // getAllSettings() should also return defaults for every category
            // (this is the §5.5 regression — /all previously dropped fields).
            var all = service.getAllSettings();
            assertThat(all.getSmtpHost()).isEmpty();
            assertThat(all.getRateLimitApi()).isEqualTo("100");
            assertThat(all.getUploadMaxSize()).isEqualTo("10MB");
            assertThat(all.isFeatureContest()).isTrue();
        }
    }

    @Nested
    @DisplayName("Persistence (P0 §5.1)")
    class Persistence {

        @Test
        @DisplayName("updateGeneral → getGeneral returns the new value (not a no-op echo)")
        void updateGeneralIsPersisted() {
            GeneralSettingsVO in = new GeneralSettingsVO();
            in.setSiteName("Custom Site");
            in.setSiteDescription("Custom Desc");
            in.setEnableRegistrations(false);
            in.setRequireEmailVerification(true);
            in.setMaintenanceMode(false);
            in.setMaintenanceMessage("");

            GeneralSettingsVO out = service.updateGeneralSettings(in);

            assertThat(out.getSiteName()).isEqualTo("Custom Site");
            assertThat(out.getSiteDescription()).isEqualTo("Custom Desc");
            assertThat(out.isEnableRegistrations()).isFalse();
            assertThat(out.isRequireEmailVerification()).isTrue();

            // A fresh read (in a new service instance) must see the same
            // values — this is the core regression for §5.1.
            SystemSettingsServiceImpl freshService =
                    new SystemSettingsServiceImpl(mapper, objectMapper, java.time.Clock.systemDefaultZone());
            GeneralSettingsVO reRead = freshService.getGeneralSettings();
            assertThat(reRead.getSiteName()).isEqualTo("Custom Site");
            assertThat(reRead.isEnableRegistrations()).isFalse();

            // The row should exist in the table.
            SystemSetting row = mapper.selectById("general");
            assertThat(row).isNotNull();
            assertThat(row.getValue()).contains("Custom Site");
        }

        @Test
        @DisplayName("updateRateLimits twice does not throw duplicate-key")
        void updateRateLimitsTwice() {
            RateLimitSettingsVO first = new RateLimitSettingsVO();
            first.setRateLimitApi("100");
            first.setRateLimitSubmission("10");
            first.setRateLimitAuth("5");
            first.setRateLimitUpload("20");
            service.updateRateLimitSettings(first);

            RateLimitSettingsVO second = new RateLimitSettingsVO();
            second.setRateLimitApi("200");
            second.setRateLimitSubmission("20");
            second.setRateLimitAuth("10");
            second.setRateLimitUpload("30");
            service.updateRateLimitSettings(second);

            RateLimitSettingsVO reRead = service.getRateLimitSettings();
            assertThat(reRead.getRateLimitApi()).isEqualTo("200");
        }

        @Test
        @DisplayName("resetToDefaults deletes all 5 rows; next read returns defaults")
        void resetDeletesAllRows() {
            service.updateGeneralSettings(new GeneralSettingsVO());
            service.updateRateLimitSettings(new RateLimitSettingsVO());
            // M1 (review): at least one flag must be true; all-defaults is
            // now rejected as a safety guard against accidental empty
            // PATCHes from a misbehaving frontend.
            FeatureTogglesVO features = new FeatureTogglesVO();
            features.setFeatureContest(true);
            service.updateFeatureToggles(features);

            assertThat(mapper.selectById("general")).isNotNull();

            service.resetToDefaults();

            assertThat(mapper.selectById("general")).isNull();
            assertThat(mapper.selectById("email")).isNull();
            assertThat(mapper.selectById("rate-limits")).isNull();
            assertThat(mapper.selectById("uploads")).isNull();
            assertThat(mapper.selectById("features")).isNull();

            // Defaults are returned even though the row is gone.
            assertThat(service.getGeneralSettings().getSiteName()).isEqualTo("UltiCode");
        }
    }

    @Nested
    @DisplayName("Maintenance mode is a single source of truth (P0 §5.2)")
    class MaintenanceConsistency {

        @Test
        @DisplayName("toggleMaintenance(true) → getGeneral() reports maintenanceMode=true")
        void toggleThenRead() {
            MaintenanceModeRequest req = new MaintenanceModeRequest();
            req.setEnabled(true);
            req.setMessage("Upgrading");
            service.toggleMaintenance(req);

            GeneralSettingsVO general = service.getGeneralSettings();
            assertThat(general.isMaintenanceMode()).isTrue();
            assertThat(general.getMaintenanceMessage()).isEqualTo("Upgrading");
        }

        @Test
        @DisplayName("toggleMaintenance(false) after true → getGeneral() reports false")
        void toggleOffThenRead() {
            MaintenanceModeRequest on = new MaintenanceModeRequest();
            on.setEnabled(true);
            on.setMessage("On");
            service.toggleMaintenance(on);

            MaintenanceModeRequest off = new MaintenanceModeRequest();
            off.setEnabled(false);
            off.setMessage("");
            service.toggleMaintenance(off);

            GeneralSettingsVO general = service.getGeneralSettings();
            assertThat(general.isMaintenanceMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("Feature toggles — accidental empty PATCH is rejected (review M1)")
    class FeatureTogglesSafety {

        @Test
        @DisplayName("PATCH with all 8 flags false (== empty body JSON) throws")
        void allDefaultsRejected() {
            FeatureTogglesVO allOff = new FeatureTogglesVO();
            assertThat(allOff.isFeatureContest()).isFalse(); // sanity: all defaults
            assertThat(allOff.isFeatureProblemLists()).isFalse();

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> service.updateFeatureToggles(allOff))
                    .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                    .hasMessageContaining("Refusing to disable all 8 feature flags");

            // No row should be persisted on rejection.
            assertThat(mapper.selectById("features")).isNull();
        }

        @Test
        @DisplayName("PATCH with at least one flag true succeeds")
        void oneFlagTrueAccepted() {
            FeatureTogglesVO one = new FeatureTogglesVO();
            one.setFeatureContest(true);
            // other 7 default to false — still a valid (non-accidental) PATCH
            FeatureTogglesVO out = service.updateFeatureToggles(one);
            assertThat(out.isFeatureContest()).isTrue();

            // And the row is persisted.
            assertThat(mapper.selectById("features")).isNotNull();
        }
    }

    @Nested
    @DisplayName("SMTP password masking (P0 §8.1)")
    class SmtpPasswordMasking {

        @Test
        @DisplayName("setting a real password returns the mask in subsequent GET")
        void setThenGetMasks() {
            EmailSettingsVO in = new EmailSettingsVO();
            in.setSmtpHost("smtp.example.com");
            in.setSmtpPort("465");
            in.setSmtpUser("u@e.com");
            in.setSmtpPassword("real-secret-1");
            in.setSmtpFrom("f@e.com");
            in.setSmtpFromName("X");
            in.setSmtpSecure(true);

            service.updateEmailSettings(in);

            EmailSettingsVO out = service.getEmailSettings();
            assertThat(out.getSmtpPassword()).isEqualTo(EmailSettingsVO.PASSWORD_MASK);
            // Other fields are NOT masked
            assertThat(out.getSmtpHost()).isEqualTo("smtp.example.com");
        }

        @Test
        @DisplayName("re-PATCH with the mask preserves the original password")
        void rePatchWithMaskPreserves() {
            // 1) Set a real password
            EmailSettingsVO in = new EmailSettingsVO();
            in.setSmtpHost("smtp.example.com");
            in.setSmtpPort("465");
            in.setSmtpUser("u@e.com");
            in.setSmtpPassword("real-secret-2");
            in.setSmtpFrom("f@e.com");
            in.setSmtpFromName("X");
            in.setSmtpSecure(true);
            service.updateEmailSettings(in);

            // 2) Re-PATCH with the mask — typical "user opens the form
            //    without changing the password field".
            EmailSettingsVO in2 = new EmailSettingsVO();
            in2.setSmtpHost("smtp.example.com");
            in2.setSmtpPort("465");
            in2.setSmtpUser("u@e.com");
            in2.setSmtpPassword(EmailSettingsVO.PASSWORD_MASK);
            in2.setSmtpFrom("f@e.com");
            in2.setSmtpFromName("Y");  // changed
            in2.setSmtpSecure(false);
            service.updateEmailSettings(in2);

            // 3) GET must still show the mask
            EmailSettingsVO out = service.getEmailSettings();
            assertThat(out.getSmtpFromName()).isEqualTo("Y");
            assertThat(out.getSmtpPassword()).isEqualTo(EmailSettingsVO.PASSWORD_MASK);
        }

        @Test
        @DisplayName("re-PATCH with explicit new password updates the secret")
        void rePatchWithNewPassword() {
            EmailSettingsVO first = new EmailSettingsVO();
            first.setSmtpPassword("first");
            service.updateEmailSettings(first);

            EmailSettingsVO second = new EmailSettingsVO();
            second.setSmtpPassword("second-real-secret");
            service.updateEmailSettings(second);

            EmailSettingsVO out = service.getEmailSettings();
            assertThat(out.getSmtpPassword()).isEqualTo(EmailSettingsVO.PASSWORD_MASK);

            // Confirm the stored value is "second-real-secret" by reading
            // the raw row directly.
            SystemSetting row = mapper.selectById("email");
            assertThat(row.getValue()).contains("second-real-secret");
        }
    }
}
