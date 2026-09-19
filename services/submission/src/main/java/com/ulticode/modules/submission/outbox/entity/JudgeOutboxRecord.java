package com.ulticode.modules.submission.outbox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.outbox.JudgeOutboxPayload;
import lombok.Data;

import java.util.Map;

/**
 * Outbox row for judge dispatch (M3a). One row per
 * {@code (submission_id, generation)} pair; the {@code uniq_dispatch} unique
 * key makes double-enqueue physically impossible even under duplicate writes.
 *
 * <p>Lifecycle states:
 * <ul>
 *   <li>{@code PENDING} — waiting for the dispatcher (M3a: shadow; M3c: real).</li>
 *   <li>{@code SENT} — dispatcher claimed and "delivered" (M3a: would-have-enqueued; M3c: enqueued).</li>
 *   <li>{@code DEAD} — exhausted retries, parked for inspection.</li>
 *   <li>{@code ARCHIVED} — soft-archived post-TTL.</li>
 * </ul>
 *
 * <p>{@code is_shadow = 1} for the entire M3a/M3b window; the M3c cutover flips
 * new rows to {@code is_shadow = 0} once the outbox dispatcher becomes the sole
 * active producer (outbox cutover contract).
 *
 * <p>{@code created_at} is intentionally <b>not</b> annotated with
 * {@link FieldFill} — it is filled by the DB {@code DEFAULT CURRENT_TIMESTAMP(3)}
 * so the timestamp is the DB clock, not the JVM clock (database-clock contract).
 *
 * <p>Moved from {@code com.ulticode.modules.queue.outbox.entity} during P7
 * submission-family cutover. Both the queue module (dispatcher) and the
 * submission module (producer) depend on this record, making it a natural
 * resident of the shared {@code backend-submission-domain} module.
 */
@Data
@TableName(value = "judge_outbox", autoResultMap = true)
public class JudgeOutboxRecord {

    /** Row primary key (UUID). Distinct from submission_id. */
    @TableId(type = IdType.INPUT)
    private String id;

    /** The submission this dispatch targets. */
    @TableField("submission_id")
    private String submissionId;

    /** Generation the submission was at when this row was written. */
    @TableField("generation")
    private Long generation;

    /**
     * Full judge job payload, serialized to the {@code json} column via
     * {@link JacksonTypeHandler}. Rows keep the {@code Map<String,Object>}
     * form for stored-JSON compatibility; the typed shape and its codec are
     * owned by {@link JudgeOutboxPayload}.
     */
    @TableField(value = "payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /** Dispatch state: {@code PENDING} / {@code SENT} / {@code DEAD} / {@code ARCHIVED}. */
    private String state;

    /**
     * Shadow flag (outbox cutover contract). {@code true} (1) for the M3a/M3b window where
     * the legacy RQueue is the sole active producer; {@code false} (0) after the
     * M3c cutover when the outbox dispatcher takes over real delivery.
     */
    @TableField("is_shadow")
    private Boolean isShadow;

    /** Number of dispatch attempts (incremented by markRetry). */
    private Integer attempts;

    /** Truncated last error message, set on retry/dead. */
    private String lastError;

    /**
     * Creation timestamp. Populated by DB {@code DEFAULT CURRENT_TIMESTAMP(3)};
     * deliberately not a {@link FieldFill} field so it stays on the DB clock.
     */
    @TableField(value = "created_at", fill = FieldFill.DEFAULT)
    private java.time.LocalDateTime createdAt;

    /** Time the dispatcher marked the row SENT; null until then. */
    @TableField("sent_at")
    private java.time.LocalDateTime sentAt;

    /** Earliest next dispatch attempt time (drives backoff via idx_state_retry). */
    @TableField("next_retry_at")
    private java.time.LocalDateTime nextRetryAt;

    // ==================== Static factories ====================

    /**
     * Build an outbox row for a freshly submitted job (generation = 1).
     *
     * @param submission   the just-inserted submission
     * @param problemId    problem id (string form for the payload)
     * @param generation   submission generation (1 for a fresh submit)
     * @param isShadow     whether this is a shadow write (M3a/M3b: true)
     * @return a new, unsaved outbox record
     */
    public static JudgeOutboxRecord of(Submission submission, String problemId,
                                       long generation, boolean isShadow,
                                       UuidGenerator uuidGenerator) {
        JudgeOutboxRecord record = baseRecord(submission, problemId, generation, isShadow);
        record.setId(uuidGenerator.newId());
        record.setState("PENDING");
        record.setAttempts(0);
        // next_retry_at and created_at default to CURRENT_TIMESTAMP(3) in DB;
        // left null here so MyBatis-Plus's NOT_NULL insert strategy lets the DB
        // defaults populate them (keeps the timestamps on the DB clock).
        return record;
    }

    /**
     * Build an outbox row for a re-enqueue (reaper recovery or rejudge) at a
     * new generation. The caller is responsible for having already bumped the
     * submission's generation; this row simply records the new dispatch intent.
     *
     * @param submission   the submission (post-bump)
     * @param problemId    problem id (string form)
     * @param newGeneration the bumped generation
     * @param isShadow     whether this is a shadow write (M3a/M3b: true)
     * @return a new, unsaved outbox record
     */
    public static JudgeOutboxRecord forResubmission(Submission submission, String problemId,
                                                    long newGeneration, boolean isShadow,
                                                    UuidGenerator uuidGenerator) {
        // Same shape as a fresh dispatch; the unique key (submission_id, generation)
        // guarantees only one row per generation regardless of how it was created.
        return of(submission, problemId, newGeneration, isShadow, uuidGenerator);
    }

    /**
     * Builds the dispatch row; the payload shape and key order are owned by
     * {@link JudgeOutboxPayload}.
     */
    private static JudgeOutboxRecord baseRecord(Submission submission, String problemId,
                                                long generation, boolean isShadow) {
        JudgeOutboxRecord record = new JudgeOutboxRecord();
        record.setSubmissionId(submission.getId());
        record.setGeneration(generation);
        record.setPayload(JudgeOutboxPayload.forSubmission(submission, problemId, generation).toMap());
        record.setIsShadow(isShadow);
        return record;
    }
}
