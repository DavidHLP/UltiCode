package com.ulticode.app.storage;

/**
 * Raised when a {@link FileStoragePort} backend fails to store, fetch or
 * delete an object. Callers translate it into their own error contract.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
