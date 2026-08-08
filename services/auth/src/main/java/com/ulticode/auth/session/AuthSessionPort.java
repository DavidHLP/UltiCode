package com.ulticode.auth.session;

import com.ulticode.auth.account.AuthAccountRecord;

/**
 * Port for issuing authentication sessions in backend-auth.
 *
 * <p>Cookie mutations are returned as data and applied by an inbound HTTP
 * adapter; this port is not coupled to Servlet APIs.</p>
 */
public interface AuthSessionPort {

    AuthSession completeLogin(AuthAccountRecord account);

    AuthSession completeRefresh(AuthAccountRecord account, String rotatedRefreshToken);

    AuthSession clearSession();
}
