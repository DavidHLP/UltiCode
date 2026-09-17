package com.ulticode.common.command;

/**
 * Claim-protocol extension of {@link CommandReceiptStore}.
 *
 * <p>Only claim-based owners finalize or delete a processing claim; a
 * mutate-then-record owner never reaches these operations, so its adapter
 * implements the base port alone.</p>
 */
public interface ClaimCommandReceiptStore extends CommandReceiptStore {

    /** Conditionally finalizes a processing claim with the encoded result. */
    int markSuccess(String id, String resultPayload);

    /** Conditionally deletes a processing claim after a failed mutation. */
    int deleteClaim(String id);
}
