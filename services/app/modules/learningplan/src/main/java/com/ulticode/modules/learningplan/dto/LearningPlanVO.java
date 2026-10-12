package com.ulticode.modules.learningplan.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Owner-scoped projection of a stored learning plan.
 *
 * <p>Never exposes the idempotency key or request fingerprint; those are
 * server-internal replay-fence values.
 */
@Data
public class LearningPlanVO {
    private String id;
    private String sourceSubmissionId;
    private Integer draftVersion;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}
