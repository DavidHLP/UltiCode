package com.ulticode.app.api.service;

/**
 * Read seam through which the submission module checks user existence
 * without importing the user module's mapper.
 *
 * <p>The user module supplies the production adapter.
 */
public interface UserExistencePort {

    /**
     * Check whether a user exists by id.
     *
     * @param userId the user id
     * @return {@code true} if the user exists; {@code false} otherwise
     */
    boolean existsById(String userId);
}
