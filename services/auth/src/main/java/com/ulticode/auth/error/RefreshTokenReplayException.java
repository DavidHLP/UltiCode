package com.ulticode.auth.error;

import com.ulticode.common.error.BaseErrorCode;

/** Rejects replay after revoking the credential family. */
public final class RefreshTokenReplayException extends AuthBusinessException {

    public RefreshTokenReplayException() {
        super(BaseErrorCode.UNAUTHORIZED, "Refresh token has been revoked — possible token theft");
    }
}
