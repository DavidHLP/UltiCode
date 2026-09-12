package com.ulticode.admin.error;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shared failure mapping for Admin read paths crossing an owner boundary.
 *
 * <p>Keeping the owner in the message makes a 503 actionable without changing
 * the established {@code Result}/{@code PageResult} envelope or leaking the
 * underlying transport exception to clients.
 */
public final class AdminReadContract {

    private static final Map<String, Map<Integer, AdminErrorCode>> PERMISSION_CODES = Map.of(
            BaseErrorCode.UNAUTHORIZED.namespace(), Map.of(
                    BaseErrorCode.UNAUTHORIZED.code(), AdminErrorCode.UNAUTHORIZED,
                    BaseErrorCode.FORBIDDEN.code(), AdminErrorCode.FORBIDDEN),
            AppErrorCode.NAMESPACE, Map.of(
                    AppErrorCode.UNAUTHORIZED.code(), AdminErrorCode.UNAUTHORIZED,
                    AppErrorCode.FORBIDDEN.code(), AdminErrorCode.FORBIDDEN),
            AuthErrorCode.NAMESPACE, Map.of(
                    BaseErrorCode.UNAUTHORIZED.code(), AdminErrorCode.UNAUTHORIZED,
                    BaseErrorCode.FORBIDDEN.code(), AdminErrorCode.FORBIDDEN,
                    AuthErrorCode.ACCOUNT_BANNED.code(), AdminErrorCode.FORBIDDEN),
            AdminErrorCode.NAMESPACE, Map.of(
                    AdminErrorCode.UNAUTHORIZED.code(), AdminErrorCode.UNAUTHORIZED,
                    AdminErrorCode.FORBIDDEN.code(), AdminErrorCode.FORBIDDEN));

    private AdminReadContract() {
    }

    /** Typed result for an owner read; unavailable values never masquerade as data. */
    public record OwnerRead<T>(boolean available, T value, String reason) {

        public static <T> OwnerRead<T> available(T value) {
            return new OwnerRead<>(true, value, null);
        }

        public static <T> OwnerRead<T> unavailable(String reason) {
            return new OwnerRead<>(false, null, reason);
        }
    }

    /** Classify one RPC result at the Admin owner boundary. */
    public static <T> OwnerRead<T> classify(String owner, RpcResult<T> result) {
        AdminErrorCode permissionCode = permissionCode(result);
        if (permissionCode != null) {
            throw permissionError(permissionCode, result.error());
        }
        return result != null && result.success()
                ? OwnerRead.available(result.data())
                : OwnerRead.unavailable(ownerMessage(owner));
    }

    /** Classify one RPC result when no more specific owner label is available. */
    public static <T> OwnerRead<T> classify(RpcResult<T> result) {
        return classify("Owner", result);
    }

    /** Return whether an RPC result is one of the permission failures in the table. */
    public static boolean isPermissionFailure(RpcResult<?> result) {
        return permissionCode(result) != null;
    }

    /** Re-throw fatal JVM failures and permission failures from a completed owner task. */
    public static void propagate(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof BusinessException exception) {
            AdminErrorCode permissionCode = permissionCode(exception.getErrorCode());
            if (permissionCode != null) {
                if (exception.getErrorCode() == permissionCode) {
                    throw exception;
                }
                throw new BusinessException(
                        permissionCode,
                        exception.getMessage(),
                        exception.getCause());
            }
        }
    }

    /** Await a heterogeneous bounded fan-out and classify each completed query. */
    @SafeVarargs
    public static <T> List<OwnerRead<T>> awaitAndClassify(
            CancellableQueryExecutor queryExecutor,
            String owner,
            long timeout,
            TimeUnit unit,
            CancellableQueryExecutor.Query<? extends T>... queries) {
        if (queries == null || queries.length == 0) {
            return List.of();
        }

        String fallbackReason = ownerMessage(owner);
        try {
            queryExecutor.awaitAll(timeout, unit, queries);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fallbackReason = ownerMessage(owner, "interrupted");
        } catch (ExecutionException exception) {
            propagate(exception.getCause());
            if (unwrap(exception.getCause()) instanceof RejectedExecutionException) {
                fallbackReason = ownerMessage(owner, "capacity exceeded");
            }
        } catch (TimeoutException exception) {
            fallbackReason = ownerMessage(owner, "timed out");
        }

        List<OwnerRead<T>> reads = new ArrayList<>(queries.length);
        for (CancellableQueryExecutor.Query<? extends T> query : queries) {
            reads.add(completed(owner, fallbackReason, query));
        }
        return List.copyOf(reads);
    }

    /** Await and classify one typed query without exposing executor exceptions. */
    public static <T> OwnerRead<T> awaitAndClassify(
            CancellableQueryExecutor queryExecutor,
            String owner,
            CancellableQueryExecutor.Query<T> query,
            long timeout,
            TimeUnit unit) {
        try {
            return OwnerRead.available(queryExecutor.await(query, timeout, unit));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return OwnerRead.unavailable(ownerMessage(owner, "interrupted"));
        } catch (ExecutionException exception) {
            propagate(exception.getCause());
            return OwnerRead.unavailable(ownerMessage(owner));
        } catch (TimeoutException exception) {
            return OwnerRead.unavailable(ownerMessage(owner, "timed out"));
        } catch (CancellationException exception) {
            return OwnerRead.unavailable(ownerMessage(owner, "cancelled"));
        }
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

    @SuppressWarnings("unchecked")
    private static <T> OwnerRead<T> completed(
            String owner,
            String fallbackReason,
            CancellableQueryExecutor.Query<? extends T> query) {
        if (query == null || !query.result().isDone() || query.result().isCancelled()) {
            return OwnerRead.unavailable(fallbackReason);
        }
        try {
            Object value = query.result().join();
            if (value instanceof OwnerRead<?> ownerRead) {
                return cast(ownerRead);
            }
            return OwnerRead.available((T) value);
        } catch (CompletionException exception) {
            propagate(exception.getCause());
            Throwable cause = unwrap(exception.getCause());
            String reason = cause instanceof RejectedExecutionException
                    ? ownerMessage(owner, "capacity exceeded") : fallbackReason;
            return OwnerRead.unavailable(reason);
        } catch (CancellationException exception) {
            return OwnerRead.unavailable(fallbackReason);
        }
    }

    private static <T> OwnerRead<T> cast(OwnerRead<?> read) {
        return (OwnerRead<T>) read;
    }

    private static <T> AdminErrorCode permissionCode(RpcResult<T> result) {
        return result == null || result.success() ? null : permissionCode(result.error());
    }

    private static AdminErrorCode permissionCode(RpcResult.ErrorPayload error) {
        if (error == null) {
            return null;
        }
        Map<Integer, AdminErrorCode> byCode = PERMISSION_CODES.get(error.namespace());
        return byCode == null ? null : byCode.get(error.code());
    }

    private static AdminErrorCode permissionCode(NamespacedErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }
        Map<Integer, AdminErrorCode> byCode = PERMISSION_CODES.get(errorCode.namespace());
        return byCode == null ? null : byCode.get(errorCode.code());
    }

    private static BusinessException permissionError(
            AdminErrorCode permissionCode, RpcResult.ErrorPayload error) {
        String message = error == null ? null : error.message();
        return message == null || message.isBlank()
                ? new BusinessException(permissionCode)
                : new BusinessException(permissionCode, message);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException || cause instanceof ExecutionException) {
            if (cause.getCause() == null) {
                break;
            }
            cause = cause.getCause();
        }
        return cause;
    }

    private static String ownerMessage(String owner) {
        return owner + " owner query unavailable";
    }

    private static String ownerMessage(String owner, String state) {
        return owner + " owner query " + state;
    }
}
