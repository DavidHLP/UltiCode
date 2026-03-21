package com.ulticode.modules.email.constants;

/**
 * Email status enumeration.
 * Represents the status of an email in the sending process.
 */
public enum EmailStatus {
    /**
     * Email is pending to be sent
     */
    PENDING,

    /**
     * Email has been sent successfully
     */
    SENT,

    /**
     * Email failed to send
     */
    FAILED
}
