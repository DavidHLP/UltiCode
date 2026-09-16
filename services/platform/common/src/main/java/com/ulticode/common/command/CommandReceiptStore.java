package com.ulticode.common.command;

/** Owner persistence port for durable command receipts. */
public interface CommandReceiptStore {

    /** Inserts a claim or recorded success and returns affected rows. */
    int insertClaim(ReceiptWrite receipt);

    /** Looks up the owner receipt by its natural idempotency key. */
    ReceiptView findByKey(String service, String operation, String idempotencyKey);

    /** Conditionally finalizes a processing claim with the encoded result. */
    int markSuccess(String id, String resultPayload);

    /** Conditionally deletes a processing claim after a failed mutation. */
    int deleteClaim(String id);
}
