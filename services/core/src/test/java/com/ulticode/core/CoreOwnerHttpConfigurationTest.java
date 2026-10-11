package com.ulticode.core;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreOwnerHttpConfigurationTest {
    @Test
    void unavailableOwnerCannotReachParentOrBusinessBeans() throws Exception {
        var owners = mock(CoreOwnerContextManager.class);
        when(owners.states()).thenReturn(Map.of("auth", CoreOwnerContextManager.State.DISABLED));
        var response = new MockHttpServletResponse();
        var reachedParent = new AtomicBoolean();
        new CoreOwnerHttpConfiguration.OwnerHttpFilter(owners).doFilter(
                new MockHttpServletRequest("POST", "/auth/login"), response,
                (request, result) -> reachedParent.set(true));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(reachedParent).isFalse();
        verify(owners, never()).bean(any(), any());
    }

    @Test
    void neighboringPathCannotSelectOwner() throws Exception {
        var owners = mock(CoreOwnerContextManager.class);
        var reachedParent = new AtomicBoolean();
        new CoreOwnerHttpConfiguration.OwnerHttpFilter(owners).doFilter(
                new MockHttpServletRequest("GET", "/administrator/users"),
                new MockHttpServletResponse(), (request, result) -> reachedParent.set(true));
        assertThat(reachedParent).isTrue();
        verifyNoInteractions(owners);
    }

    @Test
    void ownerSecurityRejectionCannotReachDispatcherOrParent() throws Exception {
        var owners = mock(CoreOwnerContextManager.class);
        var dispatcher = mock(DispatcherServlet.class);
        var security = mock(FilterChainProxy.class);
        when(owners.states()).thenReturn(Map.of("admin", CoreOwnerContextManager.State.READY));
        when(owners.bean("admin", DispatcherServlet.class)).thenReturn(dispatcher);
        when(owners.bean("admin", FilterChainProxy.class)).thenReturn(security);
        doAnswer(invocation -> {
            ((MockHttpServletResponse) invocation.getArgument(1)).setStatus(403);
            return null;
        }).when(security).doFilter(any(), any(), any());
        var response = new MockHttpServletResponse();
        var reachedParent = new AtomicBoolean();
        new CoreOwnerHttpConfiguration.OwnerHttpFilter(owners).doFilter(
                new MockHttpServletRequest("POST", "/admin/users"), response,
                (request, result) -> reachedParent.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(reachedParent).isFalse();
        verify(dispatcher, never()).service(any(ServletRequest.class), any(ServletResponse.class));
    }
}
