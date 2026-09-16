package com.ulticode.audit;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.util.AuditContext;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for the audit-identity fix on the admin problem-list delete path.
 *
 * <p>The audit record's user id must resolve from the {@code userId} method
 * parameter (which {@code AdminProblemListController} fills from
 * {@code principal.getName()}), never from the request-carried resource
 * {@code id}. Guards AGENTS.md: "Audit identity comes from the authenticated
 * principal, not request data." Before the fix, {@code @Audited(userIdFrom="id")}
 * recorded the problem-list id as the audit user.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    /** Fixture carrying a real {@link Audited} instance the aspect resolves. */
    @Audited(action = "DELETE_PROBLEM_LIST",
             entityType = "PROBLEM_LIST",
             userIdFrom = "userId",
             entityIdFrom = "id")
    void deleteProblemList(String id, String userId) {
        // no-op fixture; only its annotation is read
    }

    @Audited(action = "CREATE_TAG", entityType = "TAG")
    void createTag() {
        // no-op fixture; only its annotation is read
    }

    @Audited(action = "UPDATE_TAG", entityType = "TAG", entityIdFrom = "id")
    void updateTag(String id) {
        // no-op fixture; only its annotation is read
    }

    @Audited(action = "FAIL_TAG", entityType = "TAG")
    void failTag() {
        // no-op fixture; only its annotation is read
    }

    @Mock private AuditSinkPort auditSinkPort;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private CurrentUserProvider currentUserProvider;

    @AfterEach
    void clearThreadLocals() {
        RequestContextHolder.resetRequestAttributes();
        AuditContext.clear();
    }

    @Test
    void audit_userId_resolves_from_principal_param_not_request_id() throws Throwable {
        AuditAspect aspect = new AuditAspect(auditSinkPort, clientIpResolver, currentUserProvider);

        // id is the request-carried resource id; userId is the principal.
        ProceedingJoinPoint joinPoint = joinPoint(
                new String[]{"id", "userId"},
                new Object[]{"list-001", "admin-456"});

        aspect.auditAround(joinPoint, fixtureAnnotation());

        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entityId = ArgumentCaptor.forClass(String.class);
        verify(auditSinkPort).log(
                org.mockito.ArgumentMatchers.anyString(),
                userId.capture(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                entityId.capture(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        // The audit user is the principal parameter, NOT the request resource id.
        assertThat(userId.getValue()).isEqualTo("admin-456");
        // The request id is recorded as the ENTITY id, not the user id.
        assertThat(entityId.getValue()).isEqualTo("list-001");
    }

    @Test
    void audit_normalizes_fallback_entity_ip_and_user_agent() throws Throwable {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);
        when(clientIpResolver.resolveCurrent()).thenReturn("203.0.113.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AuditAspect aspect = new AuditAspect(auditSinkPort, clientIpResolver, currentUserProvider);
        aspect.auditAround(joinPoint(new String[0], new Object[0]), annotation("createTag"));

        verify(auditSinkPort).log(
                "system", null, "CREATE_TAG", "TAG", "N/A", null, null,
                "203.0.113.9", "Mozilla/5.0");
        verify(currentUserProvider).getCurrentUserId();
        verify(clientIpResolver).resolveCurrent();
    }

    @Test
    void audit_normalizes_empty_entity_and_user_agent() throws Throwable {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(clientIpResolver.resolveCurrent()).thenReturn("unknown");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AuditAspect aspect = new AuditAspect(auditSinkPort, clientIpResolver, currentUserProvider);
        aspect.auditAround(
                joinPoint(new String[]{"id"}, new Object[]{""}),
                annotation("updateTag", String.class));

        verify(auditSinkPort).log(
                "admin-1", null, "UPDATE_TAG", "TAG", "N/A", null, null,
                "unknown", null);
    }

    @Test
    void audit_sink_failure_replaces_business_failure_without_clearing_context() throws Throwable {
        RuntimeException businessFailure = new IllegalStateException("business failure");
        RuntimeException sinkFailure = new IllegalStateException("sink failure");
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(clientIpResolver.resolveCurrent()).thenReturn("unknown");
        doThrow(sinkFailure).when(auditSinkPort).log(
                anyString(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any());

        ProceedingJoinPoint joinPoint = joinPoint(new String[0], new Object[0]);
        when(joinPoint.proceed()).thenThrow(businessFailure);
        AuditContext.setEntityId("ctx-id");

        AuditAspect aspect = new AuditAspect(auditSinkPort, clientIpResolver, currentUserProvider);
        assertThatThrownBy(() -> aspect.auditAround(joinPoint, annotation("failTag")))
                .isSameAs(sinkFailure);
        assertThat(AuditContext.getEntityId()).isEqualTo("ctx-id");
    }

    private Audited annotation(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return AuditAspectTest.class.getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(Audited.class);
    }

    private Audited fixtureAnnotation() throws NoSuchMethodException {
        Method m = AuditAspectTest.class.getDeclaredMethod(
                "deleteProblemList", String.class, String.class);
        return m.getAnnotation(Audited.class);
    }

    private ProceedingJoinPoint joinPoint(String[] paramNames, Object[] args) throws Throwable {
        CodeSignature signature = mock(CodeSignature.class);
        when(signature.getParameterNames()).thenReturn(paramNames);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(null);
        return joinPoint;
    }
}
