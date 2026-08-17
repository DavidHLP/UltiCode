package com.ulticode.common.security;

import com.ulticode.common.auth.AccountInfo;

import java.util.Optional;

/**
 * Read port for the account projection needed by local authentication.
 */
public interface AccountReadPort {
    Optional<AccountInfo> findById(String userId);
}
