package com.ulticode.modules.auth.service.oauth;

import cn.hutool.http.HttpRequest;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic in-memory {@link OAuthHttpTransport} for unit tests.
 *
 * <p>Records every request the adapter passes in and replies with a
 * scripted sequence of {@link ScriptedResponse responses}. Lets tests
 * exercise the provider adapter (URL building, header construction, body
 * encoding, JSON parsing, error mapping) without standing up a real HTTP
 * server.
 *
 * <p>Two safety nets:
 * <ul>
 *   <li>If the adapter sends more requests than the script declares,
 *       {@link #executeForBody} throws &mdash; never returns silently.</li>
 *   <li>If the test reads more requests than the adapter sent,
 *       {@link #takeRequest()} returns {@code null} and the assertion
 *       fails loudly rather than the test racing ahead of the harness.</li>
 * </ul>
 *
 * <p>Intentionally limited to {@link #executeForBody} &mdash; the single
 * contract every provider adapter depends on. If a future method is added
 * to {@link OAuthHttpTransport}, this fake MUST be updated or the new
 * method will throw {@link UnsupportedOperationException} at runtime.
 */
public class FakeOAuthHttpTransport implements OAuthHttpTransport {

    private final Deque<ScriptedResponse> script = new ArrayDeque<>();
    private final List<RecordedCall> calls = new ArrayList<>();

    /**
     * Enqueue a successful response with the given body and HTTP 200.
     */
    public FakeOAuthHttpTransport enqueueOk(String body) {
        script.addLast(new ScriptedResponse(200, body));
        return this;
    }

    /**
     * Enqueue a non-2xx response. The transport will reject it via the
     * standard {@link BusinessException} gate so tests can assert the
     * failure-closed behavior without going through MockWebServer.
     */
    public FakeOAuthHttpTransport enqueueStatus(int status, String body) {
        script.addLast(new ScriptedResponse(status, body));
        return this;
    }

    /**
     * Number of requests the adapter has sent so far.
     */
    public int callCount() {
        return calls.size();
    }

    /**
     * Pop the next recorded request (FIFO). Returns {@code null} if no
     * request has been sent yet &mdash; callers should treat that as a
     * test failure.
     */
    public RecordedCall takeRequest() {
        return calls.isEmpty() ? null : calls.remove(0);
    }

    @Override
    public String executeForBody(HttpRequest request, String provider, String operation) {
        // Capture the request before we pop the response so an undersized
        // script still records the offending request for assertions.
        calls.add(new RecordedCall(request, provider, operation));

        ScriptedResponse next = script.pollFirst();
        if (next == null) {
            throw new AssertionError(
                "FakeOAuthHttpTransport received an unexpected request from "
                    + provider + " (" + operation + ") with no scripted response. "
                    + "Enqueue a response before exercising this code path.");
        }

        if (next.status < 200 || next.status >= 300) {
            // Mirror the production transport's fail-closed behavior: status
            // is surfaced, body is never echoed into the exception.
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                "OAuth " + provider + " " + operation + " failed: HTTP " + next.status);
        }
        return next.body;
    }

    /**
     * Recorded call for assertions on URL / method / headers / body.
     * The body string is captured via reflection on Hutool's
     * {@code bodyBytes} field because the production {@link cn.hutool.http.HttpRequest}
     * does not expose a body getter (only chainable setters).
     */
    public static final class RecordedCall {
        private final HttpRequest request;
        private final String provider;
        private final String operation;

        RecordedCall(HttpRequest request, String provider, String operation) {
            this.request = request;
            this.provider = provider;
            this.operation = operation;
        }

        public HttpRequest request() {
            return request;
        }

        public String provider() {
            return provider;
        }

        public String operation() {
            return operation;
        }

        /**
         * Best-effort read of the request body as a UTF-8 string. Returns
         * {@code null} if the body has not been set or the body field
         * cannot be read via reflection (Hutool upgrade). The
         * {@link okhttp3.mockwebserver.MockWebServer}-based
         * {@link GithubOAuthClientTest} remains the authoritative
         * source for body-encoding regressions; this getter exists to
         * let the fake-transport tests spot-check the body shape.
         */
        public String body() {
            try {
                // Hutool's HttpBase stores the body as a Resource. Walk up
                // the class hierarchy so we work regardless of which
                // subclass (HttpRequest, HttpBase) actually declares it.
                Class<?> klass = request.getClass();
                while (klass != null) {
                    try {
                        Field f = klass.getDeclaredField("body");
                        f.setAccessible(true);
                        Object resource = f.get(request);
                        if (resource == null) return null;
                        // cn.hutool.core.io.resource.Resource#readUtf8Str()
                        Method m = resource.getClass().getMethod("readUtf8Str");
                        return (String) m.invoke(resource);
                    } catch (NoSuchFieldException notHere) {
                        klass = klass.getSuperclass();
                    }
                }
                return null;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

    private record ScriptedResponse(int status, String body) {
    }
}

