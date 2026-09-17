package com.ulticode.common.command;

/**
 * Receipt protocol used by an owner command boundary.
 *
 * <p>The two modes are intentionally explicit: claim-based owners reserve the
 * key before mutation, while Auth preserves its historical mutation-then-record
 * behavior for compatibility with existing receipts.</p>
 */
public enum ReceiptExecutionMode {
    /** Mutate first, then record a successful non-null result. */
    MUTATE_THEN_RECORD,
    /** Insert a processing claim, mutate once, and finalize or delete it. */
    CLAIM_MUTATE_FINALIZE
}
