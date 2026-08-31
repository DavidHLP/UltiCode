package com.ulticode.rpc.resilience;

import com.ulticode.common.resilience.DependencyGuard;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;

/**
 * One circuit/bulkhead per logical Dubbo dependency service.
 *
 * <p>This is a cluster filter, so one permit covers the whole logical call,
 * including Dubbo's single safe query retry. Business, validation and
 * authorization failures prove the provider is reachable and do not open the
 * circuit. An open circuit or saturated bulkhead throws; it never returns a
 * fabricated successful {@link Result}.
 */
@Activate(group = CommonConstants.CONSUMER, order = -100)
public final class DubboDependencyResilienceFilter implements ClusterFilter {

    private final ConcurrentHashMap<String, DependencyGuard> guards =
            new ConcurrentHashMap<>();
    private final int maxConcurrentCalls;
    private final int failureThreshold;
    private final Duration openDuration;
    private final LongSupplier clockMillis;

    public DubboDependencyResilienceFilter() {
        this(RpcPolicy.MAX_CONCURRENT_CALLS,
                RpcPolicy.CIRCUIT_FAILURE_THRESHOLD,
                Duration.ofMillis(RpcPolicy.CIRCUIT_OPEN_MS),
                System::currentTimeMillis);
    }

    DubboDependencyResilienceFilter(
            int maxConcurrentCalls,
            int failureThreshold,
            Duration openDuration,
            LongSupplier clockMillis) {
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clockMillis = clockMillis;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String serviceKey = invoker.getUrl().getServiceKey();
        DependencyGuard guard = guards.computeIfAbsent(serviceKey, ignored ->
                new DependencyGuard(
                        maxConcurrentCalls, failureThreshold, openDuration, clockMillis));
        DependencyGuard.Permit permit;
        try {
            permit = guard.acquire();
        } catch (DependencyGuard.RejectedException rejected) {
            throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION,
                    "Dubbo dependency " + serviceKey + " rejected: " + rejected.getMessage(),
                    rejected);
        }

        try {
            Result result = invoker.invoke(invocation);
            if (result == null) {
                permit.failure();
                throw new RpcException("Dubbo dependency " + serviceKey + " returned null result");
            }
            if (result instanceof AppResponse) {
                complete(permit, result, null);
                return result;
            }
            return result.whenCompleteWithContext((response, error) ->
                    complete(permit, response, error));
        } catch (RuntimeException error) {
            if (isTransportFailure(error)) {
                permit.failure();
            } else {
                permit.success();
            }
            throw error;
        }
    }

    DependencyGuard.State stateFor(String serviceKey) {
        DependencyGuard guard = guards.get(serviceKey);
        return guard == null ? DependencyGuard.State.CLOSED : guard.state();
    }

    private static void complete(
            DependencyGuard.Permit permit, Result response, Throwable error) {
        Throwable failure = error != null
                ? error : response == null ? null : response.getException();
        if (isTransportFailure(failure)) {
            permit.failure();
        } else {
            permit.success();
        }
    }

    private static boolean isTransportFailure(Throwable failure) {
        Throwable resolved = unwrap(failure);
        if (resolved == null) {
            return false;
        }
        if (resolved instanceof RpcException rpc) {
            return !rpc.isBiz()
                    && !rpc.isValidation()
                    && !rpc.isAuthorization()
                    && !rpc.isForbidden();
        }
        return true;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable resolved = failure;
        while ((resolved instanceof CompletionException
                || resolved instanceof ExecutionException)
                && resolved.getCause() != null) {
            resolved = resolved.getCause();
        }
        return resolved;
    }
}
