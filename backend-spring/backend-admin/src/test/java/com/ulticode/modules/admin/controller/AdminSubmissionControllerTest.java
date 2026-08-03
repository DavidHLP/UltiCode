package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.UlticodeBackendApplication;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.LanguageOption;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.admin.dto.StatusOption;
import com.ulticode.modules.admin.projection.AdminSubmissionProjection;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * @WebMvcTest for AdminSubmissionController.
 *
 * <p>Targets the 6 admin submission endpoints, focusing on input validation
 * (the silent-failure bugs found in the 2026-06-09 smoke test) and basic
 * response shape. Authn/authz are tested separately in integration tests
 * (addFilters=false bypasses Spring Security here).</p>
 */
@WebMvcTest(
        value = AdminSubmissionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
// Disambiguate from BackendAdminApplication on the test classpath (P7-ADMIN-BULK-001)
@ContextConfiguration(classes = UlticodeBackendApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminSubmissionController")
class AdminSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminSubmissionService adminSubmissionService;

    @MockBean
    private com.ulticode.modules.admin.service.SubmissionCutoverService submissionCutoverService;

    @MockBean
    private AdminSubmissionProjection adminSubmissionProjection;

    // SecurityConfig dependencies
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private JwtProperties jwtProperties;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean
    private CorsProperties corsProperties;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------
    // getStatuses — derived from SubmissionStatus enum (11 entries)
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("GET /admin/submissions/statuses")
    class GetStatuses {

        @Test
        @DisplayName("returns 11 status options derived from enum")
        void returnsElevenStatuses() throws Exception {
            List<StatusOption> options = List.of(
                makeStatus("Pending", "PENDING", "pending"),
                makeStatus("Judging", "JUDGING", "pending"),
                makeStatus("Accepted", "ACCEPTED", "accepted"),
                makeStatus("Wrong Answer", "WRONG_ANSWER", "error"),
                makeStatus("Time Limit Exceeded", "TIME_LIMIT_EXCEEDED", "error"),
                makeStatus("Memory Limit Exceeded", "MEMORY_LIMIT_EXCEEDED", "error"),
                makeStatus("Output Limit Exceeded", "OUTPUT_LIMIT_EXCEEDED", "error"),
                makeStatus("Presentation Error", "PRESENTATION_ERROR", "error"),
                makeStatus("Runtime Error", "RUNTIME_ERROR", "error"),
                makeStatus("Compile Error", "COMPILE_ERROR", "error"),
                makeStatus("System Error", "SYSTEM_ERROR", "system")
            );
            when(adminSubmissionProjection.getStatuses()).thenReturn(options);

            mockMvc.perform(get("/admin/submissions/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data[10].code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.data[9].code").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.data[9].key").value("Compile Error"));
        }

        private StatusOption makeStatus(String key, String code, String category) {
            StatusOption s = new StatusOption();
            s.setKey(key);
            s.setLabel(key);
            s.setCode(code);
            s.setCategory(category);
            return s;
        }
    }

    // ------------------------------------------------------------------
    // getLanguages — returns LanguageOption[] with key + label
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("GET /admin/submissions/languages")
    class GetLanguages {

        @Test
        @DisplayName("returns 4 language options with humanised labels")
        void returnsFourLanguagesWithLabels() throws Exception {
            List<LanguageOption> languages = List.of(
                makeLang("cpp", "C++"),
                makeLang("java", "Java"),
                makeLang("javascript", "JavaScript"),
                makeLang("python", "Python")
            );
            when(adminSubmissionProjection.getLanguages()).thenReturn(languages);

            mockMvc.perform(get("/admin/submissions/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].key").value("cpp"))
                .andExpect(jsonPath("$.data[0].label").value("C++"))
                .andExpect(jsonPath("$.data[2].label").value("JavaScript"));
        }

        private LanguageOption makeLang(String key, String label) {
            LanguageOption l = new LanguageOption();
            l.setKey(key);
            l.setLabel(label);
            return l;
        }
    }

    // ------------------------------------------------------------------
    // getSubmission by id — 404 for missing
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("GET /admin/submissions/{id}")
    class GetSubmission {

        @Test
        @DisplayName("returns 404 with code 40001 when submission not found")
        void notFound_returns404WithCode() throws Exception {
            when(adminSubmissionProjection.getSubmission("nope"))
                .thenThrow(new BusinessException(AdminErrorCode.SUBMISSION_NOT_FOUND));

            mockMvc.perform(get("/admin/submissions/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40001));
        }
    }

    // ------------------------------------------------------------------
    // rejudge — @NotNull on notifyUser, response carries rejudgedAt + retryCount
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("POST /admin/submissions/{id}/rejudge")
    class Rejudge {

        @Test
        @DisplayName("empty body returns 400 (notifyUser required)")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post("/admin/submissions/sub-1/rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.notifyUser").value("notifyUser is required"));

            verify(adminSubmissionService, never()).rejudge(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("valid body returns 200 with rejudgedAt and retryCount populated")
        void valid_returnsRejudgedAtAndRetryCount() throws Exception {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId("sub-1");
            result.setSuccess(true);
            result.setOldStatus("Accepted");
            result.setNewStatus("Pending");
            result.setRejudgedAt(Instant.parse("2026-06-09T10:00:00Z"));
            result.setRetryCount(2);
            when(submissionCutoverService.rejudge("sub-1", false)).thenReturn(result);

            mockMvc.perform(post("/admin/submissions/sub-1/rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"notifyUser\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value("sub-1"))
                .andExpect(jsonPath("$.data.rejudgedAt").value("2026-06-09T10:00:00Z"))
                .andExpect(jsonPath("$.data.retryCount").value(2));
        }
    }

    // ------------------------------------------------------------------
    // batchRejudge — the high-risk surface. All input-validation bugs
    // that previously returned 200 with empty results must now 400.
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("POST /admin/submissions/batch-rejudge")
    class BatchRejudge {

        @Test
        @DisplayName("empty body returns 400 (submissionIds required)")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.submissionIds").exists());

            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
        }

        @Test
        @DisplayName("null submissionIds returns 400")
        void nullIds_returns400() throws Exception {
            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"submissionIds\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.submissionIds").exists());

            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
        }

        @Test
        @DisplayName("empty array returns 400 (not the previous silent 200)")
        void emptyArray_returns400() throws Exception {
            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"submissionIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.submissionIds").exists());

            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
        }

        @Test
        @DisplayName("51 IDs returns 400 (size limit)")
        void fiftyOneIds_returns400() throws Exception {
            StringBuilder sb = new StringBuilder("{\"submissionIds\":[");
            for (int i = 0; i < 51; i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append("id-").append(i).append('"');
            }
            sb.append("]}");

            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(sb.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.submissionIds").exists());

            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
        }

        @Test
        @DisplayName("wrong field name (subIds) returns 400")
        void wrongFieldName_returns400() throws Exception {
            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"subIds\": [\"x\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.submissionIds").exists());

            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
        }

        @Test
        @DisplayName("legacy field 'ids' is still accepted (@JsonAlias compat)")
        void legacyIdsField_isAccepted() throws Exception {
            BatchRejudgeResponse resp = new BatchRejudgeResponse();
            resp.setTotal(1);
            resp.setSuccessful(1);
            resp.setFailed(0);
            resp.setResults(List.of());
            when(submissionCutoverService.batchRejudge(anyList(), anyBoolean())).thenReturn(resp);

            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\": [\"x\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("valid body returns 200 with results array")
        void valid_returnsResults() throws Exception {
            RejudgeResult r = new RejudgeResult();
            r.setSubmissionId("sub-1");
            r.setSuccess(true);
            r.setOldStatus("Accepted");
            r.setNewStatus("Pending");
            r.setRejudgedAt(Instant.parse("2026-06-09T10:00:00Z"));
            r.setRetryCount(1);

            BatchRejudgeResponse resp = new BatchRejudgeResponse();
            resp.setTotal(1);
            resp.setSuccessful(1);
            resp.setFailed(0);
            resp.setResults(List.of(r));
            when(submissionCutoverService.batchRejudge(anyList(), anyBoolean())).thenReturn(resp);

            mockMvc.perform(post("/admin/submissions/batch-rejudge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"submissionIds\": [\"sub-1\"], \"notifyUsers\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.successful").value(1))
                .andExpect(jsonPath("$.data.results[0].submissionId").value("sub-1"))
                .andExpect(jsonPath("$.data.results[0].rejudgedAt")
                    .value("2026-06-09T10:00:00Z"));
        }
    }
}
