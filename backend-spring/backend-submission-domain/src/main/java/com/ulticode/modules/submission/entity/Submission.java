package com.ulticode.modules.submission.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ulticode.modules.submission.enums.CaseScope;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Submission entity representing the submissions table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName(value = "submissions", autoResultMap = true)
public class Submission {

    /**
     * Submission unique identifier (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * ID of the problem being submitted
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * ID of the user who submitted
     */
    @TableField("user_id")
    private String userId;

    /**
     * Programming language used (e.g., javascript, python, java)
     */
    private String language;

    /**
     * Source code submitted
     */
    private String code;

    /**
     * Submission status (e.g., Pending, Accepted, Wrong Answer)
     */
    private String status;

    /**
     * Runtime in milliseconds
     */
    private Integer runtime;

    /**
     * Memory usage in megabytes
     */
    private Double memory;

    /**
     * Additional notes
     */
    private String notes;

    /**
     * Number of times this submission has been rejudged by an admin.
     * Incremented each time an admin triggers a rejudge.
     */
    @TableField("retry_count")
    private Integer retryCount = 0;

    /**
     * Generation fence token (ADR-003 M3b). Monotonically incremented on every
     * rejudge and on every lease-expiry recovery. Verdict writes carry the
     * generation they observed at acquire time so a stale worker that wakes up
     * after a superseding rejudge cannot overwrite the newer result. Defaults to
     * {@code 1L} to match the DB column {@code NOT NULL DEFAULT 1}; legacy rows
     * are backfilled by the migration.
     */
    @TableField("generation")
    private Long generation = 1L;

    /**
     * Attempt identifier of the worker currently holding the JUDGING lease
     * (ADR-003 M3b). {@code null} when the submission is not being judged.
     * Populated by {@code acquireLease}, cleared by {@code writeVerdictFenced}
     * and the lease reaper. Used as the second fence axis so a worker that loses
     * the lease (reaper bumped generation) cannot renew or write a verdict.
     */
    @TableField("current_attempt_id")
    private String currentAttemptId;

    /**
     * Absolute expiry of the current JUDGING lease (ADR-003 M3b). The worker
     * heartbeats this forward every {@code leaseTtl/3} seconds while judging.
     * When it lapses, {@link com.ulticode.modules.submission.reaper.JudgingLeaseReaper}
     * recovers the row (bumps generation, resets to Pending, re-enqueues).
     * {@code null} when the submission is not being judged.
     */
    @TableField("judging_lease_expires_at")
    private LocalDateTime judgingLeaseExpiresAt;

    /**
     * Submission creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Runtime percentile compared to other submissions
     */
    @TableField("runtime_percentile")
    private Double runtimePercentile;

    /**
     * Memory percentile compared to other submissions
     */
    @TableField("memory_percentile")
    private Double memoryPercentile;

    /**
     * Test case execution details (JSON)
     */
    @TableField(value = "test_details", typeHandler = JacksonTypeHandler.class)
    private List<TestCaseDetail> testDetails;

    /**
     * Memory distribution bins in MB (JSON)
     */
    @TableField(value = "memoryDistBinsMb", typeHandler = JacksonTypeHandler.class)
    private Object memoryDistBinsMb;

    /**
     * Runtime distribution bins in ms (JSON)
     */
    @TableField(value = "runtimeDistBinsMs", typeHandler = JacksonTypeHandler.class)
    private Object runtimeDistBinsMs;

    /**
     * Nested class for test case details.
     *
     * <p>Existing fields ({@code status}, {@code time}, {@code memory}, {@code detail},
     * {@code output}, {@code expectedOutput}, {@code inputs}) keep their wire and
     * JSON persistence contract for backward compatibility with rows written before
     * P0-1.
     *
     * <p>Two nullable fields are added by P0-1 and are absent on legacy rows:
     * <ul>
     *   <li>{@code caseId} — the {@code test_cases.id} (varchar(40)) this case was
     *       sourced from. {@code null} on legacy rows whose cases came from the
     *       pre-existing {@code problem_examples} read path.</li>
     *   <li>{@code caseScope} — {@code SAMPLE} or {@code HIDDEN} per
     *       {@link com.ulticode.modules.submission.enums.CaseScope}. {@code null} on
     *       legacy rows; the projection layer treats {@code null} as legacy sample.
     *       Never persist {@code LEGACY_SAMPLE} — that value exists only at the
     *       user-facing projection layer.</li>
     * </ul>
     */
    @Data
    public static class TestCaseDetail {
        private String status;
        private Integer time;
        private Double memory;
        private String detail;
        private String output;
        private String expectedOutput;
        private List<InputParam> inputs;
        /** Nullable (P0-1). {@code test_cases.id} the case was sourced from. */
        private String caseId;
        /** Nullable (P0-1). See {@link com.ulticode.modules.submission.enums.CaseScope}. */
        private CaseScope caseScope;

        @Data
        public static class InputParam {
            private String id;
            private String label;
            private String name;
            private String value;
        }
    }
}
