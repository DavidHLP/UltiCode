package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.UlticodeBackendApplication;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.service.AuditService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * @WebMvcTest for AuditController.
 *
 * <p>Covers the two recent fixes:
 * <ul>
 *   <li>Unsupported export format returns the project's JSON error envelope
 *       (code/message/traceId) rather than Tomcat's HTML error page.</li>
 *   <li>CSV export preserves second-level precision in {@code createdAt} via
 *       {@code DateTimeFormatter.ISO_LOCAL_DATE_TIME}.</li>
 * </ul>
 *
 * <p>Mirrors the pattern of {@link AdminProblemListControllerTest}: bypass
 * security filters, mock the service and security dependencies.</p>
 */
@WebMvcTest(
        value = AuditController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
// Disambiguate from BackendAdminApplication on the test classpath (P7-ADMIN-BULK-001)
@ContextConfiguration(classes = UlticodeBackendApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuditController - export format handling")
class AuditControllerFormatTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditService auditService;

    // SecurityConfig dependencies (kept consistent with sibling controller tests)
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

    @Nested
    @DisplayName("GET /admin/audit/export with unsupported format")
    class UnsupportedFormatTests {

        @Test
        @DisplayName("format=xml returns 400 with JSON error envelope (not Tomcat HTML)")
        void exportAuditLogs_xmlFormat_returnsJsonErrorEnvelope() throws Exception {
            mockMvc.perform(get("/admin/audit/export").param("format", "xml"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value(containsString("Unsupported format: xml")))
                    .andExpect(jsonPath("$.traceId").exists());

            // Service must NOT be called when the format is invalid.
            verify(auditService, never()).getAuditLogsForExport(any());
        }

        @Test
        @DisplayName("format=yaml returns 400 with the same JSON error envelope")
        void exportAuditLogs_yamlFormat_returnsJsonErrorEnvelope() throws Exception {
            mockMvc.perform(get("/admin/audit/export").param("format", "yaml"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value(containsString("Unsupported format: yaml")));
        }
    }

    @Nested
    @DisplayName("GET /admin/audit/export with csv format")
    class CsvExportTests {

        @Test
        @DisplayName("createdAt field preserves second-level precision (ISO_LOCAL_DATE_TIME)")
        void exportAuditLogs_csvFormat_createdAtHasSecondsPrecision() throws Exception {
            AuditLogVO vo = new AuditLogVO();
            vo.setId("audit-log-001");
            vo.setAction("UPDATE");
            vo.setEntityType("PROBLEM");
            vo.setEntityId("1");
            vo.setIpAddress("192.168.1.100");
            vo.setCreatedAt(LocalDateTime.of(2026, 5, 30, 10, 0, 0));

            when(auditService.getAuditLogsForExport(any()))
                    .thenReturn(Collections.singletonList(vo));

            mockMvc.perform(get("/admin/audit/export").param("format", "csv"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/csv"))
                    .andExpect(header().string("Content-Disposition", containsString("audit-logs.csv")))
                    .andExpect(content().string(containsString("2026-05-30T10:00:00")));
        }

        @Test
        @DisplayName("createdAt with full precision survives (does not truncate seconds)")
        void exportAuditLogs_csvFormat_keepsArbitrarySeconds() throws Exception {
            AuditLogVO vo = new AuditLogVO();
            vo.setId("audit-log-002");
            vo.setAction("CREATE");
            vo.setEntityType("PROBLEM");
            vo.setEntityId("2");
            vo.setCreatedAt(LocalDateTime.of(2026, 1, 15, 13, 45, 27));

            when(auditService.getAuditLogsForExport(any()))
                    .thenReturn(Collections.singletonList(vo));

            mockMvc.perform(get("/admin/audit/export").param("format", "csv"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("2026-01-15T13:45:27")));
        }
    }

    @Nested
    @DisplayName("GET /admin/audit/export with json format")
    class JsonExportTests {

        @Test
        @DisplayName("format=json returns attachment with application/json content type")
        void exportAuditLogs_jsonFormat_returnsJsonAttachment() throws Exception {
            when(auditService.getAuditLogsForExport(any()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/admin/audit/export").param("format", "json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(header().string("Content-Disposition", containsString("audit-logs.json")))
                    .andExpect(content().string("[]"));
        }
    }
}
