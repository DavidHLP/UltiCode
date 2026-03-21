package com.ulticode.modules.moderation.dto;

import lombok.Data;

import java.util.List;

/**
 * View object for batch action results.
 */
@Data
public class BatchActionResultVO {

    /**
     * Number of successfully processed items
     */
    private int successCount;

    /**
     * Number of failed items
     */
    private int failureCount;

    /**
     * List of errors for failed items
     */
    private List<BatchError> errors;

    public BatchActionResultVO() {
    }

    public BatchActionResultVO(int successCount, int failureCount, List<BatchError> errors) {
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.errors = errors;
    }

    /**
     * Error details for a failed batch item.
     */
    @Data
    public static class BatchError {
        private String queueId;
        private String message;

        public BatchError() {
        }

        public BatchError(String queueId, String message) {
            this.queueId = queueId;
            this.message = message;
        }
    }
}
