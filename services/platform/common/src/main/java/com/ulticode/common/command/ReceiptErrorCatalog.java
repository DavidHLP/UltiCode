package com.ulticode.common.command;

import com.ulticode.common.error.NamespacedErrorCode;

/** Owner-supplied error namespace for protocol failures. */
public interface ReceiptErrorCatalog {

    /** Error for malformed command metadata. */
    NamespacedErrorCode invalidCommand();

    /** Error for a reused key with a different request fingerprint. */
    NamespacedErrorCode keyConflict();

    /** Error for a duplicate request whose claim is still processing. */
    NamespacedErrorCode processingDuplicate();

    /** Error when an insert conflict cannot be followed by a receipt lookup. */
    NamespacedErrorCode missingReceipt();

    /** Error when a stored successful payload cannot be replayed. */
    NamespacedErrorCode replayFailure();
}
