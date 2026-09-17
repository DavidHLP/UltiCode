package com.ulticode.common.audit;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuditRecorderTest {

    @Mock
    private AuditSinkPort auditSinkPort;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void record_emits_normalized_metadata_and_no_target_user() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);
        when(clientIpResolver.resolveCurrent()).thenReturn("198.51.100.10");
        requestWithUserAgent("Recorder/1.0");
        Map<String, Object> oldValues = Map.of("enabled", false);
        Map<String, Object> newValues = Map.of("enabled", true);

        newRecorder().record("UPDATE_SETTING", "SETTING", null, oldValues, newValues);

        verify(auditSinkPort).log(
                "system", null, "UPDATE_SETTING", "SETTING", "N/A",
                oldValues, newValues, "198.51.100.10", "Recorder/1.0");
    }

    @Test
    void recordForUser_preserves_target_user_and_normalizes_empty_metadata() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(clientIpResolver.resolveCurrent()).thenReturn("unknown");
        requestWithUserAgent("");

        newRecorder().recordForUser(
                "DELETE_USER", "USER", "", "user-7", null, Map.of("deleted", true));

        verify(auditSinkPort).log(
                "admin-1", "user-7", "DELETE_USER", "USER", "N/A",
                null, Map.of("deleted", true), "unknown", null);
    }

    @Test
    void record_propagates_sink_failure() {
        IllegalStateException sinkFailure = new IllegalStateException("sink failure");
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(clientIpResolver.resolveCurrent()).thenReturn("unknown");
        doThrow(sinkFailure).when(auditSinkPort).log(
                anyString(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any());

        assertThatThrownBy(() -> newRecorder().record(
                "UPDATE_SETTING", "SETTING", "setting-1", null, null))
                .isSameAs(sinkFailure);
    }

    private DefaultAuditRecorder newRecorder() {
        return new DefaultAuditRecorder(auditSinkPort, clientIpResolver, currentUserProvider);
    }

    private void requestWithUserAgent(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
