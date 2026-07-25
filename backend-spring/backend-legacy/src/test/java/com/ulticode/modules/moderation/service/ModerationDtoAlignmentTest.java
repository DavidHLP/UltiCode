package com.ulticode.modules.moderation.service;

import com.ulticode.modules.moderation.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for moderation DTO field alignment with frontend types.
 * These tests verify that backend DTOs have all fields expected by the frontend.
 */
@DisplayName("Moderation DTO Alignment Tests")
class ModerationDtoAlignmentTest {

    @Test
    @DisplayName("QueryModerationQueueDTO should have primaryCategory field")
    void queryModerationQueueDTO_hasPrimaryCategory() {
        var dto = new QueryModerationQueueDTO();
        dto.setPrimaryCategory("SPAM");
        assertThat(dto.getPrimaryCategory()).isEqualTo("SPAM");
    }

    @Test
    @DisplayName("QueryModerationQueueDTO should have minPriority field")
    void queryModerationQueueDTO_hasMinPriority() {
        var dto = new QueryModerationQueueDTO();
        dto.setMinPriority(3);
        assertThat(dto.getMinPriority()).isEqualTo(3);
    }

    @Test
    @DisplayName("QueryModerationQueueDTO should have sortBy and sortOrder fields")
    void queryModerationQueueDTO_hasSortFields() {
        var dto = new QueryModerationQueueDTO();
        dto.setSortBy("priority");
        dto.setSortOrder("asc");
        assertThat(dto.getSortBy()).isEqualTo("priority");
        assertThat(dto.getSortOrder()).isEqualTo("asc");
    }

    @Test
    @DisplayName("QueryModerationQueueDTO should have assignedTo field (not assignedToId)")
    void queryModerationQueueDTO_hasAssignedTo() {
        var dto = new QueryModerationQueueDTO();
        dto.setAssignedTo("mod-1");
        assertThat(dto.getAssignedTo()).isEqualTo("mod-1");
    }

    @Test
    @DisplayName("QueryReportsDTO should have entityType and entityId fields")
    void queryReportsDTO_hasEntityFields() {
        var dto = new QueryReportsDTO();
        dto.setEntityType("forum_post");
        dto.setEntityId("post-1");
        assertThat(dto.getEntityType()).isEqualTo("forum_post");
        assertThat(dto.getEntityId()).isEqualTo("post-1");
    }

    @Test
    @DisplayName("QueryReportsDTO should have sortBy and sortOrder fields")
    void queryReportsDTO_hasSortFields() {
        var dto = new QueryReportsDTO();
        dto.setSortBy("createdAt");
        dto.setSortOrder("desc");
        assertThat(dto.getSortBy()).isEqualTo("createdAt");
        assertThat(dto.getSortOrder()).isEqualTo("desc");
    }

    @Test
    @DisplayName("QueryAppealsDTO should have sortBy and sortOrder fields")
    void queryAppealsDTO_hasSortFields() {
        var dto = new QueryAppealsDTO();
        dto.setSortBy("createdAt");
        dto.setSortOrder("asc");
        assertThat(dto.getSortBy()).isEqualTo("createdAt");
        assertThat(dto.getSortOrder()).isEqualTo("asc");
    }

    @Test
    @DisplayName("AssignDTO should use assignedTo field name")
    void assignDTO_hasAssignedTo() {
        var dto = new AssignDTO();
        dto.setAssignedTo("mod-1");
        assertThat(dto.getAssignedTo()).isEqualTo("mod-1");
    }

    @Test
    @DisplayName("BatchActionResultVO should use failureCount (not errorCount)")
    void batchActionResultVO_hasFailureCount() {
        var result = new BatchActionResultVO(5, 2, java.util.List.of());
        assertThat(result.getFailureCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("BatchActionResultVO.BatchError should use message (not error)")
    void batchError_hasMessage() {
        var error = new BatchActionResultVO.BatchError("q1", "Not found");
        assertThat(error.getMessage()).isEqualTo("Not found");
    }

    @Test
    @DisplayName("BatchModerationActionDTO should have durationDays field")
    void batchModerationActionDTO_hasDurationDays() {
        var dto = new BatchModerationActionDTO();
        dto.setDurationDays(7);
        assertThat(dto.getDurationDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("AppealStatsVO should have all required fields")
    void appealStatsVO_hasAllFields() {
        var stats = new AppealStatsVO();
        stats.setTotalPending(5);
        stats.setTotalUnderReview(3);
        stats.setTotalApproved(10);
        stats.setTotalRejected(2);
        stats.setAvgReviewTimeHours(4.5);
        assertThat(stats.getTotalPending()).isEqualTo(5);
        assertThat(stats.getTotalUnderReview()).isEqualTo(3);
        assertThat(stats.getTotalApproved()).isEqualTo(10);
        assertThat(stats.getTotalRejected()).isEqualTo(2);
        assertThat(stats.getAvgReviewTimeHours()).isEqualTo(4.5);
    }
}
