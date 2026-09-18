package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.admin.error.AdminWebExceptionHandler;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

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
@WebMvcTest(AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AuditController.class, AdminWebExceptionHandler.class})
@DisplayName("AuditController - export format handling")
class AuditControllerFormatTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditService auditService;


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
    @DisplayName("audit query validation")
    class QueryValidationTests {

        @Test
        @DisplayName("logs reject page zero with the standard JSON error envelope")
        void logsRejectInvalidPage() throws Exception {
            mockMvc.perform(get("/admin/audit/logs").param("page", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.data.page").value("Page must be at least 1"));

            verify(auditService, never()).getAuditLogs(any());
        }

        @Test
        @DisplayName("logs reject an oversized performer ID with the standard JSON error envelope")
        void logsRejectOversizedPerformerId() throws Exception {
            mockMvc.perform(get("/admin/audit/logs").param("performerId", "x".repeat(41)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.performerId")
                            .value("Performer ID must not exceed 40 characters"));

            verify(auditService, never()).getAuditLogs(any());
        }

        @Test
        @DisplayName("stats reject an oversized limit with the standard JSON error envelope")
        void statsRejectOversizedLimit() throws Exception {
            mockMvc.perform(get("/admin/audit/stats").param("limit", "1001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.data.limit").value("Limit must not exceed 1000"));

            verify(auditService, never()).getAuditStats(any());
        }

        @Test
        @DisplayName("stats reject an oversized entity type with the standard JSON error envelope")
        void statsRejectOversizedEntityType() throws Exception {
            mockMvc.perform(get("/admin/audit/stats").param("entityType", "x".repeat(65)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.entityType")
                            .value("Entity type must not exceed 64 characters"));

            verify(auditService, never()).getAuditStats(any());
        }

        @Test
        @DisplayName("exports reject an oversized search with the standard JSON error envelope")
        void exportRejectOversizedSearch() throws Exception {
            mockMvc.perform(get("/admin/audit/export")
                            .param("search", "x".repeat(201)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.data.search")
                            .value("Search query must not exceed 200 characters"));

            verify(auditService, never()).getAuditLogsForExport(any());
        }

        @Test
        @DisplayName("exports reject an oversized action with the standard JSON error envelope")
        void exportRejectOversizedAction() throws Exception {
            mockMvc.perform(get("/admin/audit/export").param("action", "x".repeat(65)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.action")
                            .value("Action must not exceed 64 characters"));

            verify(auditService, never()).getAuditLogsForExport(any());
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
        @Test
        @DisplayName("CSV prefixes formula-leading performer usernames")
        void exportAuditLogs_csvFormat_neutralizesFormulaLeadingValues() throws Exception {
            AuditLogVO vo = new AuditLogVO();
            vo.setId("audit-log-003");
            vo.setAction("CREATE");
            vo.setEntityType("PROBLEM");
            vo.setEntityId("3");
            vo.setPerformer(new AuditLogVO.PerformerInfo(
                    "performer-1", "=1", "Test User", "ADMIN"));

            when(auditService.getAuditLogsForExport(any()))
                    .thenReturn(Collections.singletonList(vo));

            mockMvc.perform(get("/admin/audit/export").param("format", "csv"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(",'=1,")));
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
