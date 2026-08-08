package com.ulticode.auth.service;

/**
 * HTTP-neutral password reset workflow owned by backend-auth.
 *
 * <p>The inbound HTTP adapter owns request DTO validation and response
 * mapping. Token hashing, expiry, account lookup, email dispatch, password
 * replacement, reset-token cleanup, and refresh-token revocation stay behind
 * this workflow.</p>
 */
public interface PasswordResetWorkflow {

    /**
     * Starts a password reset request without revealing whether the account exists.
     *
     * @param email the requested account email
     */
    void forgotPassword(String email);

    /**
     * Replaces the account password for a valid, unexpired reset token.
     *
     * @param token the raw token supplied by the reset link
     * @param newPassword the replacement password
     */
    void resetPassword(String token, String newPassword);
}
