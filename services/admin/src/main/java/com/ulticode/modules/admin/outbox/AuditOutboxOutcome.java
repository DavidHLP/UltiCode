package com.ulticode.modules.admin.outbox;

/**
 * Owner-local outcome of the Admin audit outbox process/failure paths.
 *
 * <p>Replaces raw affected-row counts at the processor seam so retryable,
 * terminal, and lost-claim states are observable without reverse-engineering
 * mapper row counts. The SQL remains the source of truth for the row state.</p>
 */
public enum AuditOutboxOutcome {

    /** The audit log row was written and the outbox row marked PROCESSED. */
    RECORDED,

    /** The failure was recorded; the row remains retryable below its ceiling. */
    FAILED_RETRYABLE,

    /** The failure was recorded and the row reached its terminal attempt. */
    FAILED_TERMINAL,

    /** The claim was lost before the update; another worker owns or finished the row. */
    LOST_CLAIM
}
