package com.ulticode.audit;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock private AuditSinkPort auditSinkPort;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private CurrentUserProvider currentUserProvider;

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
