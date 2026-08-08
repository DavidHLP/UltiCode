package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.AccountInfo;

import java.util.Optional;

/**
 * Read port for user account info needed by websocket authentication.
 */
public interface AccountReadPort {
    Optional<AccountInfo> findById(String userId);
}
