package com.ulticode.modules.moderation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ModerationErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.*;
import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
import com.ulticode.modules.moderation.projection.ModerationProjection;
import com.ulticode.modules.moderation.service.ModerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * @WebMvcTest for ModerationController.
 *
 * <p>Uses addFilters=false to bypass all security filters, isolating the
 * controller layer for request/response contract testing.</p>
 *
 * <p>Note: @PreAuthorize annotations require method security. With addFilters=false
 * and no security context, these endpoints are accessible without authentication.
 * Authorization is tested separately in integration tests.</p>
 */
@WebMvcTest(controllers = ModerationController.class)
@org.springframework.context.annotation.Import(com.ulticode.app.error.ModerationWebExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ModerationController")
class ModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ModerationService moderationService;

    @MockBean
    private ModerationProjection moderationProjection;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    // ==================== Test Data Factory ====================

    private ModerationQueueVO buildQueueVO() {
        ModerationQueueVO vo = new ModerationQueueVO();
        vo.setId("queue-1");
        vo.setEntityType("ForumPost");
        vo.setEntityId("post-1");
        vo.setAuthorId("user-1");
        vo.setAuthorName("Test Author");
        vo.setAuthorUsername("testauthor");
        vo.setPriority(5);
        vo.setStatus("PENDING");
        vo.setReportCount(3);
        vo.setPrimaryCategory("SPAM");
        vo.setCreatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        vo.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        return vo;
    }

    private ModerationQueueVO buildQueueVOWithAssignment() {
        ModerationQueueVO vo = buildQueueVO();
        vo.setStatus("UNDER_REVIEW");
        vo.setAssignedToId("mod-1");
        vo.setAssignedToName("Moderator One");
        vo.setAssignedToUsername("moderator1");
        vo.setAssignedAt(LocalDateTime.of(2026, 6, 2, 14, 0));
        return vo;
    }

    private ModerationQueueVO buildResolvedQueueVO() {
        ModerationQueueVO vo = buildQueueVO();
        vo.setStatus("RESOLVED");
        vo.setResolution("DELETED");
        vo.setResolutionNote("Content removed for spam violation");
        vo.setReviewedById("mod-1");
        vo.setReviewedByName("Moderator One");
        vo.setReviewedAt(LocalDateTime.of(2026, 6, 3, 9, 0));
        vo.setResolvedAt(LocalDateTime.of(2026, 6, 3, 9, 0));
        return vo;
    }

    private PageResult<ModerationQueueVO> buildPageResult() {
        return PageResult.of(List.of(buildQueueVO()), 1L, 1, 10);
    }

    private ModerationStatsVO buildStatsVO() {
        ModerationStatsVO vo = new ModerationStatsVO();
        vo.setPendingCount(10L);
        vo.setUnderReviewCount(5L);
        vo.setResolvedCount(100L);
        vo.setDismissedCount(20L);
        vo.setResolvedToday(3L);
        vo.setAvgResolutionTimeHours(4.5);
        vo.setPendingAppealsCount(2L);
        Map<String, Long> byCategory = new HashMap<>();
        byCategory.put("SPAM", 50L);
        byCategory.put("HARASSMENT", 30L);
        vo.setByCategory(byCategory);
        Map<String, Long> byEntityType = new HashMap<>();
        byEntityType.put("ForumPost", 60L);
        byEntityType.put("Solution", 40L);
        vo.setByEntityType(byEntityType);
        return vo;
    }

    private ReportVO buildReportVO() {
        ReportVO vo = new ReportVO();
        vo.setId("report-1");
        vo.setReporterId("user-2");
        vo.setReporterName("Reporter Two");
        vo.setReporterUsername("reporter2");
        vo.setEntityType("ForumPost");
        vo.setEntityId("post-1");
        vo.setCategory("SPAM");
        vo.setReason("This is spam content");
        vo.setStatus("PENDING");
        vo.setCreatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        return vo;
    }

    private PageResult<ReportVO> buildReportPageResult() {
        return PageResult.of(List.of(buildReportVO()), 1L, 1, 10);
    }

    private AppealVO buildAppealVO() {
        AppealVO vo = new AppealVO();
        vo.setId("appeal-1");
        vo.setQueueId("queue-1");
        vo.setAppellantId("user-1");
        vo.setAppellantName("Test Author");
        vo.setAppellantUsername("testauthor");
        vo.setReason("I believe this moderation was incorrect");
        vo.setStatus("PENDING");
        vo.setCreatedAt(LocalDateTime.of(2026, 6, 4, 12, 0));
        return vo;
    }

    private AppealVO buildReviewedAppealVO() {
        AppealVO vo = buildAppealVO();
        vo.setStatus("APPROVED");
        vo.setReviewedById("mod-1");
        vo.setReviewedByName("Moderator One");
        vo.setReviewedAt(LocalDateTime.of(2026, 6, 5, 15, 0));
        vo.setResponse("The appeal has been reviewed and approved.");
        return vo;
    }

    private PageResult<AppealVO> buildAppealPageResult() {
        return PageResult.of(List.of(buildAppealVO()), 1L, 1, 10);
    }

    private AppealStatsVO buildAppealStatsVO() {
        AppealStatsVO vo = new AppealStatsVO();
        vo.setTotalPending(5L);
        vo.setTotalUnderReview(3L);
        vo.setTotalApproved(10L);
        vo.setTotalRejected(2L);
        vo.setAvgReviewTimeHours(4.5);
        return vo;
    }

    // ==================== Queue GET Tests ====================

    @Nested
    @DisplayName("GET /moderation/queue")
    class GetQueueTests {

        @Test
        @DisplayName("should return 200 with paginated queue items")
        void getQueueItems_success() throws Exception {
            when(moderationProjection.listQueueItems(any())).thenReturn(buildPageResult());

            mockMvc.perform(get("/moderation/queue")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].id").value("queue-1"))
                    .andExpect(jsonPath("$.data.items[0].entityType").value("ForumPost"))
                    .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.pageSize").value(10));
        }

        @Test
        @DisplayName("should return 200 with empty page when no items")
        void getQueueItems_empty() throws Exception {
            PageResult<ModerationQueueVO> emptyResult = PageResult.of(Collections.emptyList(), 0L, 1, 10);
            when(moderationProjection.listQueueItems(any())).thenReturn(emptyResult);

            mockMvc.perform(get("/moderation/queue"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    @Nested
    @DisplayName("GET /moderation/queue/{id}")
    class GetQueueItemTests {

        @Test
        @DisplayName("should return 200 with queue item details")
        void getQueueItem_success() throws Exception {
            when(moderationProjection.queueItemById("queue-1")).thenReturn(buildQueueVO());

            mockMvc.perform(get("/moderation/queue/queue-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("queue-1"))
                    .andExpect(jsonPath("$.data.entityType").value("ForumPost"))
                    .andExpect(jsonPath("$.data.entityId").value("post-1"))
                    .andExpect(jsonPath("$.data.priority").value(5))
                    .andExpect(jsonPath("$.data.reportCount").value(3))
                    .andExpect(jsonPath("$.data.primaryCategory").value("SPAM"));
        }

        @Test
        @DisplayName("should return 404 when queue item not found")
        void getQueueItem_notFound() throws Exception {
            when(moderationProjection.queueItemById("nonexistent"))
                    .thenThrow(new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND));

            mockMvc.perform(get("/moderation/queue/nonexistent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /moderation/queue/entity/{entityType}/{entityId}")
    class GetQueueItemByEntityTests {

        @Test
        @DisplayName("should return 200 with queue item for entity")
        void getQueueItemByEntity_success() throws Exception {
            when(moderationProjection.queueItemByEntity("ForumPost", "post-1"))
                    .thenReturn(buildQueueVO());

            mockMvc.perform(get("/moderation/queue/entity/ForumPost/post-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("queue-1"))
                    .andExpect(jsonPath("$.data.entityType").value("ForumPost"))
                    .andExpect(jsonPath("$.data.entityId").value("post-1"));
        }
    }

    @Nested
    @DisplayName("GET /moderation/queue/stats")
    class GetStatsTests {

        @Test
        @DisplayName("should return 200 with moderation statistics")
        void getStats_success() throws Exception {
            when(moderationProjection.stats()).thenReturn(buildStatsVO());

            mockMvc.perform(get("/moderation/queue/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.pendingCount").value(10))
                    .andExpect(jsonPath("$.data.underReviewCount").value(5))
                    .andExpect(jsonPath("$.data.resolvedCount").value(100))
                    .andExpect(jsonPath("$.data.dismissedCount").value(20))
                    .andExpect(jsonPath("$.data.avgResolutionTimeHours").value(4.5))
                    .andExpect(jsonPath("$.data.pendingAppealsCount").value(2));
        }

        @Test
        @DisplayName("should include byCategory and byEntityType breakdowns")
        void getStats_includesByCategoryAndByEntityType() throws Exception {
            // Verifies fix for LOW #3: byCategory and byEntityType must be non-null
            // and contain group-by counts. Mock returns a populated map.
            Map<String, Long> byCategory = new HashMap<>();
            byCategory.put("SPAM", 50L);
            byCategory.put("HARASSMENT", 30L);
            Map<String, Long> byEntityType = new HashMap<>();
            byEntityType.put("ForumPost", 60L);
            byEntityType.put("Solution", 40L);

            ModerationStatsVO stats = buildStatsVO();
            stats.setByCategory(byCategory);
            stats.setByEntityType(byEntityType);
            when(moderationProjection.stats()).thenReturn(stats);

            mockMvc.perform(get("/moderation/queue/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.byCategory.SPAM").value(50))
                    .andExpect(jsonPath("$.data.byCategory.HARASSMENT").value(30))
                    .andExpect(jsonPath("$.data.byEntityType.ForumPost").value(60))
                    .andExpect(jsonPath("$.data.byEntityType.Solution").value(40));
        }
    }

    // ==================== Queue Write Tests ====================

    @Nested
    @DisplayName("POST /moderation/queue/{id}/claim")
    class ClaimItemTests {

        @Test
        @DisplayName("should return 200 with claimed queue item")
        void claimItem_success() throws Exception {
            when(moderationService.claimItem(eq("queue-1"), any())).thenReturn(buildQueueVOWithAssignment());

            mockMvc.perform(post("/moderation/queue/queue-1/claim"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("queue-1"))
                    .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.data.assignedToId").value("mod-1"));
        }

        @Test
        @DisplayName("should return 404 when queue item not found")
        void claimItem_notFound() throws Exception {
            when(moderationService.claimItem(eq("nonexistent"), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND));

            mockMvc.perform(post("/moderation/queue/nonexistent/claim"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /moderation/queue/{id}/assign")
    class AssignItemTests {

        @Test
        @DisplayName("should return 200 with assigned queue item")
        void assignItem_success() throws Exception {
            when(moderationService.assignItem(eq("queue-1"), any(), eq("mod-2")))
                    .thenReturn(buildQueueVOWithAssignment());

            AssignDTO dto = new AssignDTO();
            dto.setAssignedTo("mod-2");

            mockMvc.perform(post("/moderation/queue/queue-1/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("queue-1"));
        }

        @Test
        @DisplayName("should return 404 when queue item not found")
        void assignItem_notFound() throws Exception {
            when(moderationService.assignItem(eq("nonexistent"), any(), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND));

            AssignDTO dto = new AssignDTO();
            dto.setAssignedTo("mod-2");

            mockMvc.perform(post("/moderation/queue/nonexistent/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /moderation/queue/{id}/unassign")
    class UnassignItemTests {

        @Test
        @DisplayName("should return 200 with unassigned queue item")
        void unassignItem_success() throws Exception {
            ModerationQueueVO unassigned = buildQueueVO();
            unassigned.setStatus("PENDING");
            when(moderationService.unassignItem(eq("queue-1"), any())).thenReturn(unassigned);

            mockMvc.perform(patch("/moderation/queue/queue-1/unassign"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("queue-1"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("should return 404 when queue item not found")
        void unassignItem_notFound() throws Exception {
            when(moderationService.unassignItem(eq("nonexistent"), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND));

            mockMvc.perform(patch("/moderation/queue/nonexistent/unassign"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /moderation/queue/{id}/action")
    class PerformActionTests {

        @Test
        @DisplayName("should return 200 with resolved queue item on DELETED action")
        void performAction_deleted() throws Exception {
            when(moderationService.performAction(eq("queue-1"), any(), any()))
                    .thenReturn(buildResolvedQueueVO());

            PerformModerationActionDTO dto = new PerformModerationActionDTO();
            dto.setAction(ModerationActionType.DELETED);
            dto.setNote("Spam content removed");

            mockMvc.perform(post("/moderation/queue/queue-1/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("queue-1"))
                    .andExpect(jsonPath("$.data.resolution").value("DELETED"));
        }

        @Test
        @DisplayName("should return 200 on TEMP_BANNED action with durationDays")
        void performAction_tempBanned() throws Exception {
            ModerationQueueVO result = buildResolvedQueueVO();
            result.setResolution("TEMP_BANNED");
            when(moderationService.performAction(eq("queue-1"), any(), any())).thenReturn(result);

            PerformModerationActionDTO dto = new PerformModerationActionDTO();
            dto.setAction(ModerationActionType.TEMP_BANNED);
            dto.setDurationDays(7);

            mockMvc.perform(post("/moderation/queue/queue-1/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.resolution").value("TEMP_BANNED"));
        }

        @Test
        @DisplayName("should return 400 when action is null")
        void performAction_validationError_nullAction() throws Exception {
            String json = "{\"note\":\"some note\"}";

            mockMvc.perform(post("/moderation/queue/queue-1/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when durationDays is 0")
        void performAction_validationError_durationZero() throws Exception {
            PerformModerationActionDTO dto = new PerformModerationActionDTO();
            dto.setAction(ModerationActionType.TEMP_BANNED);
            dto.setDurationDays(0);

            mockMvc.perform(post("/moderation/queue/queue-1/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when durationDays exceeds 3650")
        void performAction_validationError_durationExceedsMax() throws Exception {
            PerformModerationActionDTO dto = new PerformModerationActionDTO();
            dto.setAction(ModerationActionType.TEMP_BANNED);
            dto.setDurationDays(4000);

            mockMvc.perform(post("/moderation/queue/queue-1/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when queue item not found")
        void performAction_notFound() throws Exception {
            when(moderationService.performAction(eq("nonexistent"), any(), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND));

            PerformModerationActionDTO dto = new PerformModerationActionDTO();
            dto.setAction(ModerationActionType.DISMISSED);

            mockMvc.perform(post("/moderation/queue/nonexistent/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /moderation/queue/batch-action")
    class BatchActionTests {

        @Test
        @DisplayName("should return 200 with batch result")
        void batchAction_success() throws Exception {
            BatchActionResultVO result = new BatchActionResultVO(2, 0, Collections.emptyList());
            when(moderationService.batchAction(any(), any())).thenReturn(result);

            BatchModerationActionDTO dto = new BatchModerationActionDTO();
            dto.setQueueIds(List.of("queue-1", "queue-2"));
            dto.setAction(ModerationActionType.DELETED);

            mockMvc.perform(post("/moderation/queue/batch-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.successCount").value(2))
                    .andExpect(jsonPath("$.data.failureCount").value(0));
        }

        @Test
        @DisplayName("should return 200 with partial failures")
        void batchAction_partialFailures() throws Exception {
            BatchActionResultVO result = new BatchActionResultVO(1, 1,
                    List.of(new BatchActionResultVO.BatchError("queue-2", "Item not found")));
            when(moderationService.batchAction(any(), any())).thenReturn(result);

            BatchModerationActionDTO dto = new BatchModerationActionDTO();
            dto.setQueueIds(List.of("queue-1", "queue-2"));
            dto.setAction(ModerationActionType.DISMISSED);

            mockMvc.perform(post("/moderation/queue/batch-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.successCount").value(1))
                    .andExpect(jsonPath("$.data.failureCount").value(1))
                    .andExpect(jsonPath("$.data.errors[0].queueId").value("queue-2"));
        }

        @Test
        @DisplayName("should return 200 with all-failed details (no 400)")
        void batchAction_allFailed_returns200WithErrors() throws Exception {
            // Behavior change: all-failed now returns 200 + per-item errors instead of 400.
            BatchActionResultVO result = new BatchActionResultVO(0, 3, List.of(
                    new BatchActionResultVO.BatchError("fake-1", "Moderation queue item not found"),
                    new BatchActionResultVO.BatchError("fake-2", "Moderation queue item not found"),
                    new BatchActionResultVO.BatchError("fake-3", "Moderation queue item not found")
            ));
            when(moderationService.batchAction(any(), any())).thenReturn(result);

            BatchModerationActionDTO dto = new BatchModerationActionDTO();
            dto.setQueueIds(List.of("fake-1", "fake-2", "fake-3"));
            dto.setAction(ModerationActionType.DISMISSED);
            dto.setNote("all-failed test");

            mockMvc.perform(post("/moderation/queue/batch-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.successCount").value(0))
                    .andExpect(jsonPath("$.data.failureCount").value(3))
                    .andExpect(jsonPath("$.data.errors", org.hamcrest.Matchers.hasSize(3)))
                    .andExpect(jsonPath("$.data.errors[0].queueId").value("fake-1"))
                    .andExpect(jsonPath("$.data.errors[0].message")
                            .value(org.hamcrest.Matchers.containsString("not found")));
        }

        @Test
        @DisplayName("should return 400 when queueIds is empty")
        void batchAction_validationError_emptyQueueIds() throws Exception {
            BatchModerationActionDTO dto = new BatchModerationActionDTO();
            dto.setQueueIds(Collections.emptyList());
            dto.setAction(ModerationActionType.DELETED);

            mockMvc.perform(post("/moderation/queue/batch-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when action is null")
        void batchAction_validationError_nullAction() throws Exception {
            BatchModerationActionDTO dto = new BatchModerationActionDTO();
            dto.setQueueIds(List.of("queue-1"));

            mockMvc.perform(post("/moderation/queue/batch-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== Report Tests ====================

    @Nested
    @DisplayName("POST /moderation/reports")
    class CreateReportTests {

        @Test
        @DisplayName("should return 200 on successful report creation")
        void createReport_success() throws Exception {
            doNothing().when(moderationService).createReport(any(), any());

            CreateReportDTO dto = new CreateReportDTO();
            dto.setEntityType("ForumPost");
            dto.setEntityId("post-1");
            dto.setCategory("SPAM");
            dto.setReason("This is spam");

            mockMvc.perform(post("/moderation/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("should return 400 when entityType is missing")
        void createReport_validationError_missingEntityType() throws Exception {
            CreateReportDTO dto = new CreateReportDTO();
            dto.setEntityId("post-1");
            dto.setCategory("SPAM");

            mockMvc.perform(post("/moderation/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 when already reported")
        void createReport_alreadyReported() throws Exception {
            doThrow(new BusinessException(ModerationErrorCode.ALREADY_REPORTED))
                    .when(moderationService).createReport(any(), any());

            CreateReportDTO dto = new CreateReportDTO();
            dto.setEntityType("ForumPost");
            dto.setEntityId("post-1");
            dto.setCategory("SPAM");

            mockMvc.perform(post("/moderation/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /moderation/reports")
    class GetReportsTests {

        @Test
        @DisplayName("should return 200 with paginated reports")
        void getReports_success() throws Exception {
            when(moderationProjection.listReports(any())).thenReturn(buildReportPageResult());

            mockMvc.perform(get("/moderation/reports")
                            .param("page", "1")
                            .param("limit", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].id").value("report-1"))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.pageSize").value(10));
        }

        @Test
        @DisplayName("should return 200 with empty page when no reports")
        void getReports_empty() throws Exception {
            PageResult<ReportVO> emptyResult = PageResult.of(Collections.emptyList(), 0L, 1, 20);
            when(moderationProjection.listReports(any())).thenReturn(emptyResult);

            mockMvc.perform(get("/moderation/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    @Nested
    @DisplayName("GET /moderation/reports/{id}")
    class GetReportTests {

        @Test
        @DisplayName("should return 200 with report details")
        void getReport_success() throws Exception {
            when(moderationProjection.reportById("report-1")).thenReturn(buildReportVO());

            mockMvc.perform(get("/moderation/reports/report-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("report-1"))
                    .andExpect(jsonPath("$.data.entityType").value("ForumPost"))
                    .andExpect(jsonPath("$.data.category").value("SPAM"));
        }

        @Test
        @DisplayName("should return 404 when report not found")
        void getReport_notFound() throws Exception {
            // Service reuses MODERATION_QUEUE_NOT_FOUND for missing reports —
            // a dedicated MODERATION_REPORT_NOT_FOUND should be added
            when(moderationProjection.reportById("nonexistent"))
                    .thenThrow(new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND));

            mockMvc.perform(get("/moderation/reports/nonexistent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /moderation/reports/entity/{entityType}/{entityId}")
    class GetReportsForEntityTests {

        @Test
        @DisplayName("should return 200 with reports for entity")
        void getReportsForEntity_success() throws Exception {
            when(moderationProjection.reportsForEntity("ForumPost", "post-1"))
                    .thenReturn(List.of(buildReportVO()));

            mockMvc.perform(get("/moderation/reports/entity/ForumPost/post-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value("report-1"));
        }

        @Test
        @DisplayName("should return 200 with empty list when no reports")
        void getReportsForEntity_empty() throws Exception {
            when(moderationProjection.reportsForEntity("ForumPost", "nonexistent"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/moderation/reports/entity/ForumPost/nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== Appeal Tests ====================

    @Nested
    @DisplayName("GET /moderation/appeals")
    class GetAppealsTests {

        @Test
        @DisplayName("should return 200 with paginated appeals")
        void getAppeals_success() throws Exception {
            when(moderationProjection.listAppeals(any())).thenReturn(buildAppealPageResult());

            mockMvc.perform(get("/moderation/appeals")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].id").value("appeal-1"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Nested
    @DisplayName("GET /moderation/appeals/my")
    class GetMyAppealsTests {

        @Test
        @DisplayName("should return 200 with user's appeals")
        void getMyAppeals_success() throws Exception {
            when(moderationProjection.myAppeals(any())).thenReturn(List.of(buildAppealVO()));

            mockMvc.perform(get("/moderation/appeals/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value("appeal-1"));
        }
    }

    @Nested
    @DisplayName("GET /moderation/appeals/{id}")
    class GetAppealTests {

        @Test
        @DisplayName("should return 200 with appeal details")
        void getAppeal_success() throws Exception {
            when(moderationService.getAppeal(eq("appeal-1"), any())).thenReturn(buildAppealVO());

            mockMvc.perform(get("/moderation/appeals/appeal-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("appeal-1"))
                    .andExpect(jsonPath("$.data.queueId").value("queue-1"))
                    .andExpect(jsonPath("$.data.reason").value("I believe this moderation was incorrect"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("should return 404 when appeal not found")
        void getAppeal_notFound() throws Exception {
            when(moderationService.getAppeal(eq("nonexistent"), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.APPEAL_NOT_FOUND));

            mockMvc.perform(get("/moderation/appeals/nonexistent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user is neither appellant nor moderator")
        void getAppeal_forbidden_nonOwnerNonModerator() throws Exception {
            // Service guard rejects when currentUserId != appellantId and user lacks MOD/ADMIN role.
            when(moderationService.getAppeal(eq("appeal-1"), any()))
                    .thenThrow(new BusinessException(BaseErrorCode.FORBIDDEN));

            mockMvc.perform(get("/moderation/appeals/appeal-1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
        }
    }

    @Nested
    @DisplayName("GET /moderation/appeals/stats")
    class GetAppealStatsTests {

        @Test
        @DisplayName("should return 200 with appeal statistics")
        void getAppealStats_success() throws Exception {
            when(moderationProjection.appealStats()).thenReturn(buildAppealStatsVO());

            mockMvc.perform(get("/moderation/appeals/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalPending").value(5))
                    .andExpect(jsonPath("$.data.totalUnderReview").value(3))
                    .andExpect(jsonPath("$.data.totalApproved").value(10))
                    .andExpect(jsonPath("$.data.totalRejected").value(2))
                    .andExpect(jsonPath("$.data.avgReviewTimeHours").value(4.5));
        }
    }

    @Nested
    @DisplayName("POST /moderation/appeals")
    class CreateAppealTests {

        @Test
        @DisplayName("should return 200 on successful appeal creation")
        void createAppeal_success() throws Exception {
            when(moderationService.createAppeal(any(), any())).thenReturn(buildAppealVO());

            CreateAppealDTO dto = new CreateAppealDTO();
            dto.setQueueId("queue-1");
            dto.setReason("I believe this moderation was incorrect");
            dto.setEvidence("Original content was not spam");

            mockMvc.perform(post("/moderation/appeals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("appeal-1"));
        }

        @Test
        @DisplayName("should return 400 when queueId is missing")
        void createAppeal_validationError_missingQueueId() throws Exception {
            CreateAppealDTO dto = new CreateAppealDTO();
            dto.setReason("Some reason");

            mockMvc.perform(post("/moderation/appeals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when reason is missing")
        void createAppeal_validationError_missingReason() throws Exception {
            CreateAppealDTO dto = new CreateAppealDTO();
            dto.setQueueId("queue-1");

            mockMvc.perform(post("/moderation/appeals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /moderation/appeals/{id}/review")
    class ReviewAppealTests {

        @Test
        @DisplayName("should return 200 with reviewed appeal on APPROVED")
        void reviewAppeal_approved() throws Exception {
            when(moderationService.reviewAppeal(eq("appeal-1"), any(), any()))
                    .thenReturn(buildReviewedAppealVO());

            ReviewAppealDTO dto = new ReviewAppealDTO();
            dto.setDecision("APPROVED");
            dto.setResponse("The appeal has been reviewed and approved.");

            mockMvc.perform(post("/moderation/appeals/appeal-1/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("appeal-1"))
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }

        @Test
        @DisplayName("should return 200 on REJECTED decision")
        void reviewAppeal_rejected() throws Exception {
            AppealVO rejected = buildReviewedAppealVO();
            rejected.setStatus("REJECTED");
            rejected.setResponse("The moderation action was justified.");
            when(moderationService.reviewAppeal(eq("appeal-1"), any(), any()))
                    .thenReturn(rejected);

            ReviewAppealDTO dto = new ReviewAppealDTO();
            dto.setDecision("REJECTED");
            dto.setResponse("The moderation action was justified.");

            mockMvc.perform(post("/moderation/appeals/appeal-1/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }

        @Test
        @DisplayName("should return 404 when appeal not found")
        void reviewAppeal_notFound() throws Exception {
            when(moderationService.reviewAppeal(eq("nonexistent"), any(), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.APPEAL_NOT_FOUND));

            ReviewAppealDTO dto = new ReviewAppealDTO();
            dto.setDecision("APPROVED");

            mockMvc.perform(post("/moderation/appeals/nonexistent/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 when appeal already reviewed")
        void reviewAppeal_alreadyReviewed() throws Exception {
            when(moderationService.reviewAppeal(eq("appeal-1"), any(), any()))
                    .thenThrow(new BusinessException(ModerationErrorCode.APPEAL_ALREADY_REVIEWED));

            ReviewAppealDTO dto = new ReviewAppealDTO();
            dto.setDecision("APPROVED");

            mockMvc.perform(post("/moderation/appeals/appeal-1/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when decision is missing")
        void reviewAppeal_validationError_missingDecision() throws Exception {
            ReviewAppealDTO dto = new ReviewAppealDTO();

            mockMvc.perform(post("/moderation/appeals/appeal-1/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== Enums Endpoint ====================

    @Nested
    @DisplayName("GET /moderation/enums")
    class GetEnumsTests {

        @Test
        @DisplayName("should return 200 with moderation enum values")
        void getEnums_success() throws Exception {
            mockMvc.perform(get("/moderation/enums"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.actionTypes").isArray())
                    .andExpect(jsonPath("$.data.statuses").isArray());
        }
    }
}