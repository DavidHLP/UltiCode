package com.ulticode.submission.api.dto;

import java.io.Serializable;

/** Entity-free Submission date bucket returned by the owner read seam. */
public record SubmissionDashboardChartDataDTO(String date, long count) implements Serializable {
    private static final long serialVersionUID = 1L;
}
