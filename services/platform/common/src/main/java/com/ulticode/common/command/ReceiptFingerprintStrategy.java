package com.ulticode.common.command;

/** Owner-supplied request fingerprint strategy and compatibility matcher. */
public interface ReceiptFingerprintStrategy<C> {

    /** Returns the stable fingerprint for the command's business payload. */
    String fingerprint(C command);

    /** Matches a stored fingerprint, allowing an owner to retain legacy forms. */
    default boolean matches(String storedFingerprint, C command) {
        return fingerprint(command).equals(storedFingerprint);
    }
}
