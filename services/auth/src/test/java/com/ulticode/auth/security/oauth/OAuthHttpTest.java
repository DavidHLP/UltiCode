package com.ulticode.auth.security.oauth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.common.resilience.DependencyGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OAuth outbound HTTP resilience")
class OAuthHttpTest {

    @Test
    void serverFailureOpensCircuitAndRejectsWithoutAnotherRequest() {
        HttpRequest request = requestReturning(503);
        AtomicLong now = new AtomicLong();
        OAuthHttp transport = new OAuthHttp(ignored -> new DependencyGuard(
                1, 1, Duration.ofSeconds(30), now::get));

        assertThatThrownBy(() -> transport.executeForBody(request, "github", "user info"))
                .isInstanceOf(AuthBusinessException.class);
        assertThatThrownBy(() -> transport.executeForBody(request, "github", "user info"))
                .isInstanceOf(AuthBusinessException.class)
                .hasMessageContaining("temporarily unavailable");

        verify(request, times(1)).execute();
    }

    @Test
    void callerErrorDoesNotOpenCircuit() {
        HttpRequest request = requestReturning(401);
        OAuthHttp transport = new OAuthHttp(ignored -> new DependencyGuard(
                1, 1, Duration.ofSeconds(30)));

        assertThatThrownBy(() -> transport.executeForBody(request, "google", "token exchange"))
                .isInstanceOf(AuthBusinessException.class);
        assertThatThrownBy(() -> transport.executeForBody(request, "google", "token exchange"))
                .isInstanceOf(AuthBusinessException.class)
                .hasMessageNotContaining("temporarily unavailable");

        verify(request, times(2)).execute();
    }

    private static HttpRequest requestReturning(int status) {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.setConnectionTimeout(anyInt())).thenReturn(request);
        when(request.setReadTimeout(anyInt())).thenReturn(request);
        when(request.execute()).thenReturn(response);
        when(response.isOk()).thenReturn(false);
        when(response.getStatus()).thenReturn(status);
        return request;
    }
}
