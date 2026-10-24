package com.ulticode.app.user.port;

import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;

import java.util.function.Supplier;

final class AuthAccountReadSupport {

    private AuthAccountReadSupport() {
    }

    static <T> T ownerCall(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    static AuthAccountDTO accountOrNull(RpcResult<AuthAccountDTO> response) {
        if (response != null && !response.success() && response.error() != null
                && AuthErrorCode.NAMESPACE.equals(response.error().namespace())
                && response.error().code() == AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
            return null;
        }
        requireSuccess(response);
        return response.data();
    }

    static void requireSuccess(RpcResult<?> response) {
        if (response == null || !response.success()) {
            throw unavailable();
        }
    }

    static BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth account query unavailable");
    }
}
