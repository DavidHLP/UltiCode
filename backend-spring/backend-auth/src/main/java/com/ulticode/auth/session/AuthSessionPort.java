package com.ulticode.auth.session;

import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Port for completing authenticated sessions in backend-auth.
 */
public interface AuthSessionPort {

    LoginResponse completeLogin(AuthAccountRecord account, HttpServletResponse response);

    LoginResponse completeRefresh(AuthAccountRecord account, String rotatedRefreshToken, HttpServletResponse response);

    void clearSession(HttpServletResponse response);
}
