package com.ulticode.modules.moderation.dto;

import lombok.Data;

@Data
public class AppealStatsVO {
    private long totalPending;
    private long totalUnderReview;
    private long totalApproved;
    private long totalRejected;
    private Double avgReviewTimeHours;
}
