package com.ulticode.admin.error;

import com.ulticode.common.exception.BusinessException;

/**
 * Shared failure mapping for Admin read paths crossing an owner boundary.
 *
 * <p>Keeping the owner in the message makes a 503 actionable without changing
 * the established {@code Result}/{@code PageResult} envelope or leaking the
 * underlying transport exception to clients.
 */
public final class AdminReadContract {

    private AdminReadContract() {
    }

    /** Map a missing, invalid, timed-out, or otherwise failed owner read. */
    public static BusinessException ownerUnavailable(String owner) {
        return new BusinessException(
                AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                owner + " owner query unavailable");
    }

    /** Map an owner read failure while preserving the transport cause. */
    public static BusinessException ownerUnavailable(String owner, Throwable cause) {
        return new BusinessException(
                AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                owner + " owner query unavailable",
                cause);
    }
}
