package com.ulticode.modules.queue.job;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.constants.QueueConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Judge job definition for code evaluation tasks.
 * Represents a single code submission that needs to be judged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeJob implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique job identifier.
     */
    private String id;

    /**
     * Submission ID being judged.
     */
    private String submissionId;

    /**
     * Problem ID.
     */
    private String problemId;

    /**
     * User ID who submitted the code.
     */
    private String userId;

    /**
     * Programming language.
     */
    private String language;

    /**
     * Source code to judge.
     */
    private String code;

    /**
     * Time limit in milliseconds.
     */
    @Builder.Default
    private int timeLimitMs = 2000;

    /**
     * Memory limit in kilobytes.
     */
    @Builder.Default
    private int memoryLimitKb = 256 * 1024; // 256 MB

    /**
     * Job priority.
     */
    @Builder.Default
    private QueueConstants.Priority priority = QueueConstants.Priority.HIGH;

    /**
     * Current status.
     */
    @Builder.Default
    private QueueConstants.JobStatus status = QueueConstants.JobStatus.PENDING;

    /**
     * Number of attempts made.
     */
    @Builder.Default
    private int attempts = 0;

    /**
     * Maximum retry attempts.
     */
    @Builder.Default
    private int maxRetries = QueueConstants.DEFAULT_MAX_RETRIES;

    /**
     * Job creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Additional test case configuration.
     */
    private Map<String, Object> testCaseConfig;

    /**
     * Create a new JudgeJob with generated ID.
     *
     * @param submissionId the submission ID
     * @param problemId    the problem ID
     * @param userId       the user ID
     * @param language     the programming language
     * @param code         the source code
     * @param clock        the clock source for timestamps
     * @param uuidGenerator the id generator
     * @return the created JudgeJob
     */
    public static JudgeJob create(String submissionId, String problemId, String userId,
                                   String language, String code, Clock clock,
                                   UuidGenerator uuidGenerator) {
        return JudgeJob.builder()
                .id(uuidGenerator.newId())
                .submissionId(submissionId)
                .problemId(problemId)
                .userId(userId)
                .language(language)
                .code(code)
                .status(QueueConstants.JobStatus.PENDING)
                .createdAt(LocalDateTime.now(clock))
                .build();
    }
}
