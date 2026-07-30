package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.admin.config.MapperConfig;
import com.ulticode.admin.security.AdminSecurityConfig;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.admin.dto.settings.AllSettingsVO;
import com.ulticode.modules.admin.dto.settings.EmailSettingsVO;
import com.ulticode.modules.admin.dto.settings.FeatureTogglesVO;
import com.ulticode.modules.admin.dto.settings.GeneralSettingsVO;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeRequest;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeVO;
import com.ulticode.modules.admin.dto.settings.RateLimitSettingsVO;
import com.ulticode.modules.admin.dto.settings.UploadSettingsVO;
import com.ulticode.modules.admin.service.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for AdminSettingsController.
 *
 * <p>Mirrors {@link AdminProblemListControllerTest}: {@code addFilters=false}
 * bypasses security; auth is tested separately. The {@code @MockBean
 * SystemSettingsService} lets the tests focus on routing, JSON shape, and
 * validation wiring — persistence is exercised in
 * {@code SystemSettingsServiceImplIT} with Testcontainers.
 */
@WebMvcTest(
        value = AdminSettingsController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@Import(AdminSecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminSettingsController")
@WithMockUser(roles = "ADMIN")
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SystemSettingsService service;
    @MockBean
    private CurrentUserProvider currentUserProvider;


    // ===== fixtures =====

    private static GeneralSettingsVO sampleGeneral() {
        GeneralSettingsVO v = new GeneralSettingsVO();
        v.setMaintenanceMode(false);
        v.setMaintenanceMessage("ok");
        v.setEnableRegistrations(true);
        v.setSiteName("UltiCode");
        v.setSiteDescription("Test desc");
        v.setRequireEmailVerification(false);
        return v;
    }

    private static EmailSettingsVO sampleEmail() {
        EmailSettingsVO v = new EmailSettingsVO();
        v.setSmtpHost("smtp.example.com");
        v.setSmtpPort("587");
        v.setSmtpUser("u@e.com");
        v.setSmtpPassword(EmailSettingsVO.PASSWORD_MASK);
        v.setSmtpFrom("f@e.com");
        v.setSmtpFromName("Test");
        v.setSmtpSecure(false);
        return v;
    }

    // ===== read paths (6) =====

    @Nested
    @DisplayName("GET endpoints")
    class GetEndpoints {

        @Test
        @DisplayName("GET /admin/settings returns general settings")
        void getGeneral() throws Exception {
            when(service.getGeneralSettings()).thenReturn(sampleGeneral());
            mockMvc.perform(get("/admin/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.siteName").value("UltiCode"));
        }

        @Test
        @DisplayName("GET /admin/settings/all returns all categories")
        void getAll() throws Exception {
            when(service.getAllSettings()).thenReturn(new AllSettingsVO());
            mockMvc.perform(get("/admin/settings/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("GET /admin/settings/email masks password")
        void getEmail() throws Exception {
            when(service.getEmailSettings()).thenReturn(sampleEmail());
            mockMvc.perform(get("/admin/settings/email"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.smtpPassword").value("***"));
        }

        @Test
        @DisplayName("GET /admin/settings/rate-limits")
        void getRateLimits() throws Exception {
            when(service.getRateLimitSettings()).thenReturn(new RateLimitSettingsVO());
            mockMvc.perform(get("/admin/settings/rate-limits"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /admin/settings/uploads")
        void getUploads() throws Exception {
            when(service.getUploadSettings()).thenReturn(new UploadSettingsVO());
            mockMvc.perform(get("/admin/settings/uploads"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /admin/settings/features")
        void getFeatures() throws Exception {
            when(service.getFeatureToggles()).thenReturn(new FeatureTogglesVO());
            mockMvc.perform(get("/admin/settings/features"))
                    .andExpect(status().isOk());
        }
    }

    // ===== write paths (5 PATCH + 3 POST) =====

    @Nested
    @DisplayName("PATCH endpoints")
    class PatchEndpoints {

        @Test
        @DisplayName("PATCH /admin/settings updates general")
        void patchGeneral() throws Exception {
            when(service.updateGeneralSettings(any())).thenReturn(sampleGeneral());
            String body = "{\"siteName\":\"NewName\",\"enableRegistrations\":false}";
            mockMvc.perform(patch("/admin/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.siteName").value("UltiCode"));
        }

        @Test
        @DisplayName("PATCH /admin/settings/email updates SMTP")
        void patchEmail() throws Exception {
            when(service.updateEmailSettings(any())).thenReturn(sampleEmail());
            String body = "{\"smtpHost\":\"smtp.example.com\",\"smtpPort\":\"465\",\"smtpUser\":\"u@e.com\",\"smtpPassword\":\"newpass\",\"smtpFrom\":\"f@e.com\",\"smtpFromName\":\"X\",\"smtpSecure\":true}";
            mockMvc.perform(patch("/admin/settings/email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /admin/settings/rate-limits")
        void patchRateLimits() throws Exception {
            when(service.updateRateLimitSettings(any())).thenReturn(new RateLimitSettingsVO());
            String body = "{\"rateLimitApi\":\"200\",\"rateLimitSubmission\":\"20\",\"rateLimitAuth\":\"10\",\"rateLimitUpload\":\"30\"}";
            mockMvc.perform(patch("/admin/settings/rate-limits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /admin/settings/uploads")
        void patchUploads() throws Exception {
            when(service.updateUploadSettings(any())).thenReturn(new UploadSettingsVO());
            String body = "{\"uploadMaxSize\":\"20MB\",\"uploadAllowedTypes\":\"jpg,png\",\"uploadMaxFiles\":\"10\"}";
            mockMvc.perform(patch("/admin/settings/uploads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /admin/settings/features")
        void patchFeatures() throws Exception {
            when(service.updateFeatureToggles(any())).thenReturn(new FeatureTogglesVO());
            String body = "{\"featureContest\":true,\"featureForum\":true,\"featureSolutions\":true,\"featureSubscriptions\":true,\"featureAchievements\":true,\"featureNotifications\":true,\"featureBookmarks\":true,\"featureProblemLists\":true}";
            mockMvc.perform(patch("/admin/settings/features")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST endpoints")
    class PostEndpoints {

        @Test
        @DisplayName("POST /admin/settings/maintenance (enable)")
        void postMaintenanceEnable() throws Exception {
            MaintenanceModeVO out = new MaintenanceModeVO();
            out.setMaintenanceMode(true);
            out.setMessage("Updating");
            when(service.toggleMaintenance(any())).thenReturn(out);

            mockMvc.perform(post("/admin/settings/maintenance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":true,\"message\":\"Updating\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.maintenanceMode").value(true))
                    .andExpect(jsonPath("$.data.message").value("Updating"));
        }

        @Test
        @DisplayName("POST /admin/settings/maintenance (disable)")
        void postMaintenanceDisable() throws Exception {
            MaintenanceModeVO out = new MaintenanceModeVO();
            out.setMaintenanceMode(false);
            when(service.toggleMaintenance(any())).thenReturn(out);

            mockMvc.perform(post("/admin/settings/maintenance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.maintenanceMode").value(false));
        }

        @Test
        @DisplayName("POST /admin/settings/cache/clear")
        void postCacheClear() throws Exception {
            when(service.clearCache()).thenReturn(new com.ulticode.modules.admin.dto.ClearCacheResponseVO(java.util.List.of("settings"), "2026-07-19T00:00:00"));
            mockMvc.perform(post("/admin/settings/cache/clear"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.clearedScopes[0]").value("settings"));
        }

        @Test
        @DisplayName("POST /admin/settings/reset")
        void postReset() throws Exception {
            when(service.resetToDefaults()).thenReturn(new AllSettingsVO());
            mockMvc.perform(post("/admin/settings/reset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    // ===== negative cases =====

    @Nested
    @DisplayName("Validation and error handling")
    class NegativeCases {

        @Test
        @DisplayName("POST /admin/settings/maintenance with empty body returns 400 (NotNull on enabled)")
        void maintenanceMissingEnabled() throws Exception {
            mockMvc.perform(post("/admin/settings/maintenance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.enabled").exists());
        }

        @Test
        @DisplayName("PATCH /admin/settings/features with all-false returns 400 (review M1: all-defaults rejected)")
        void featuresAllDefaultsRejected() throws Exception {
            when(service.updateFeatureToggles(any()))
                    .thenThrow(new com.ulticode.common.exception.BusinessException(
                            com.ulticode.admin.error.AdminErrorCode.SETTING_INVALID_VALUE,
                            "Refusing to disable all 8 feature flags"));

            mockMvc.perform(patch("/admin/settings/features")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(200002));
        }

        @Test
        @DisplayName("PATCH with malformed JSON returns 400 (HttpMessageNotReadable handler)")
        void malformedJson() throws Exception {
            mockMvc.perform(patch("/admin/settings/features")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not_valid_json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("PATCH with type-mismatched field returns 400 (HttpMessageNotReadable)")
        void typeMismatch() throws Exception {
            mockMvc.perform(patch("/admin/settings/features")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"featureContest\":\"yes\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    // ===== routing verification =====

    @Test
    @DisplayName("PATCH /admin/settings/rate-limits delegates to service")
    void patchRateLimitsDelegates() throws Exception {
        when(service.updateRateLimitSettings(any())).thenReturn(new RateLimitSettingsVO());
        String body = "{\"rateLimitApi\":\"300\"}";
        mockMvc.perform(patch("/admin/settings/rate-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(service).updateRateLimitSettings(any(RateLimitSettingsVO.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin role is forbidden by method security")
    void nonAdminRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }
}
