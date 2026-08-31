package com.ulticode.auth.security.oauth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.common.resilience.DependencyGuard;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Production implementation of {@link OAuthHttpTransport} for backend-auth.
 */
@Component
public class OAuthHttp implements OAuthHttpTransport {

    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_CONCURRENT_CALLS = 8;
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, DependencyGuard> guards = new ConcurrentHashMap<>();
    private final Function<String, DependencyGuard> guardFactory;

    public OAuthHttp() {
        this(ignored -> new DependencyGuard(
                MAX_CONCURRENT_CALLS, FAILURE_THRESHOLD, OPEN_DURATION));
    }

    OAuthHttp(Function<String, DependencyGuard> guardFactory) {
        this.guardFactory = guardFactory;
    }

    @Override
    public String executeForBody(HttpRequest request, String provider, String operation) {
        DependencyGuard guard = guards.computeIfAbsent(provider, guardFactory);
        DependencyGuard.Permit permit;
        try {
            permit = guard.acquire();
        } catch (DependencyGuard.RejectedException rejected) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                    "OAuth " + provider + " temporarily unavailable", rejected);
        }
        try (permit;
             HttpResponse resp = request
                     .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                     .setReadTimeout(READ_TIMEOUT_MS)
                     .execute()) {
            if (resp.isOk()) {
                String body = resp.body();
                permit.success();
                return body;
            }
            int status = resp.getStatus();
            if (status == 429 || status >= 500) {
                permit.failure();
            } else {
                permit.success();
            }
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                    "OAuth " + provider + " " + operation + " failed: HTTP " + status);
        } catch (AuthBusinessException exception) {
            permit.ignore();
            throw exception;
        } catch (RuntimeException exception) {
            permit.failure();
            throw exception;
        }
    }
}
