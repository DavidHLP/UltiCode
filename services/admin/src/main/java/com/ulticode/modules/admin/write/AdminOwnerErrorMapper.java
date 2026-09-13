package com.ulticode.modules.admin.write;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;

/** One fail-closed translation table for owner RPC failures at the admin edge. */
public final class AdminOwnerErrorMapper {

    public enum Owner {
        CONTEST,
        NOTIFICATION,
        CONTENT_MODERATION,
        PROBLEM,
        SUBMISSION,
        FORUM_POST,
        FORUM_COMMENT,
        FORUM_TAG,
        PROBLEM_LIST
    }

    private AdminOwnerErrorMapper() {
    }

    public static BusinessException mapOwnerError(Owner owner, RpcResult<?> result) {
        if (owner == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "Owner is required to map an RPC error");
        }
        if (result == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC result is null (transport failure)");
        }
        RpcResult.ErrorPayload error = result.error();
        if (error == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC failed without error payload");
        }

        int code = error.code();
        if (code == AppErrorCode.BAD_REQUEST.code()) {
            return failure(owner == Owner.PROBLEM_LIST
                    ? AdminErrorCode.VALIDATION_FAILED : AdminErrorCode.BAD_REQUEST, error);
        }
        if (code == AppErrorCode.UNAUTHORIZED.code()) {
            return failure(AdminErrorCode.UNAUTHORIZED, error);
        }
        if (code == AppErrorCode.FORBIDDEN.code()) {
            return failure(AdminErrorCode.FORBIDDEN, error);
        }
        if (code == AppErrorCode.CONTENT_NOT_FOUND.code()) {
            return failure(notFoundCode(owner), error);
        }
        if (code == AppErrorCode.PROBLEM_NOT_FOUND.code()
                && (owner == Owner.PROBLEM || owner == Owner.PROBLEM_LIST)) {
            return failure(AdminErrorCode.PROBLEM_NOT_FOUND, error);
        }
        if (code == AppErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE.code()
                && owner == Owner.PROBLEM_LIST) {
            return failure(AdminErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE, error);
        }
        if (code == AppErrorCode.FORUM_TAG_NAME_CONFLICT.code()
                && owner == Owner.FORUM_TAG) {
            return failure(AdminErrorCode.FORUM_TAG_NAME_EXISTS, error);
        }
        if (code == AppErrorCode.FORUM_TAG_SLUG_CONFLICT.code()
                && owner == Owner.FORUM_TAG) {
            return failure(AdminErrorCode.FORUM_TAG_SLUG_EXISTS, error);
        }
        if (code == AppErrorCode.VERSION_CONFLICT.code()
                || code == AppErrorCode.CONTENT_STATE_CONFLICT.code()
                || code == AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()) {
            return failure(AdminErrorCode.CONFLICT, error);
        }
        if (code == AdminErrorCode.VALIDATION_FAILED.code()
                && owner == Owner.PROBLEM_LIST) {
            return failure(AdminErrorCode.VALIDATION_FAILED, error);
        }
        return failure(AdminErrorCode.UNKNOWN_ERROR, error);
    }

    private static AdminErrorCode notFoundCode(Owner owner) {
        return switch (owner) {
            case CONTEST -> AdminErrorCode.CONTEST_NOT_FOUND;
            case PROBLEM -> AdminErrorCode.PROBLEM_NOT_FOUND;
            case SUBMISSION -> AdminErrorCode.SUBMISSION_NOT_FOUND;
            case FORUM_TAG -> AdminErrorCode.FORUM_TAG_NOT_FOUND;
            case PROBLEM_LIST -> AdminErrorCode.PROBLEM_LIST_NOT_FOUND;
            case NOTIFICATION, CONTENT_MODERATION, FORUM_POST, FORUM_COMMENT -> AdminErrorCode.NOT_FOUND;
        };
    }

    private static BusinessException failure(
            AdminErrorCode target, RpcResult.ErrorPayload error) {
        return new BusinessException(target, error.message());
    }
}
