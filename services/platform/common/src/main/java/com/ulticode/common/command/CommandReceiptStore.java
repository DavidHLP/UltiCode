package com.ulticode.common.command;

/**
 * Owner persistence port for durable command receipts.
 *
 * <p>Mutate-then-record owners need only insertion and lookup.
 * Claim-protocol owners implement {@link ClaimCommandReceiptStore}, which adds
 * the conditional finalize/delete operations that protocol uses.</p>
 */
public interface CommandReceiptStore {

    /** Inserts a claim or recorded success and returns affected rows. */
    int insert(ReceiptWrite receipt);

    /** Looks up the owner receipt by its natural idempotency key. */
    ReceiptView findByKey(String service, String operation, String idempotencyKey);
}
