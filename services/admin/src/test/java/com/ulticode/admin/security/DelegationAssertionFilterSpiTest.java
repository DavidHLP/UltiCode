package com.ulticode.admin.security;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class DelegationAssertionFilterSpiTest {

    private static final String DESCRIPTOR =
            "META-INF/dubbo/org.apache.dubbo.rpc.Filter";
    private static final String SECRET = "01234567890123456789012345678901";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RpcContext.getClientAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void filterIsRegisteredAsDubboSpiAndCanEmitARealSignedAssertion() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(DESCRIPTOR)) {
            assertThat(stream).isNotNull();
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(descriptor).contains(
                    "delegationAssertionConsumer=" + DelegationAssertionConsumerFilter.class.getName());
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin-1", "",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        DelegationAssertionSigner signer = new DelegationAssertionSigner();
        ReflectionTestUtils.setField(signer, "secret", SECRET);
        ReflectionTestUtils.setField(signer, "issuer", DelegationAssertionContract.ISSUER);
        ReflectionTestUtils.setField(signer, "ttlSeconds", 30L);

        assertThat(signer.issueForTarget("backend-untrusted")).isNull();

        Filter filter = getExtensionLoader(Filter.class)
                .getExtension("delegationAssertionConsumer");
        assertThat(filter).isInstanceOf(DelegationAssertionConsumerFilter.class);
        ((DelegationAssertionConsumerFilter) filter).setDelegationAssertionSigner(signer);

        Invoker<?> invoker = mock(Invoker.class);
        URL target = mock(URL.class);
        when(target.getParameter("application")).thenReturn("backend-app");
        when(invoker.getUrl()).thenReturn(target);
        Invocation invocation = mock(Invocation.class);
        when(invoker.invoke(any())).thenAnswer(ignored -> {
            String assertion = RpcContext.getClientAttachment().getAttachment(
                    DelegationAssertionContract.ATTACHMENT_KEY);
            assertThat(assertion).isNotBlank();
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(DelegationAssertionContract.ISSUER)
                    .requireAudience(DelegationAssertionContract.AUDIENCE)
                    .build()
                    .parseSignedClaims(assertion)
                    .getPayload();
            assertThat(claims.getSubject()).isEqualTo("admin-1");
            assertThat(claims.getId()).isNotBlank();
            assertThat(claims.get(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, String.class))
                    .isEqualTo("backend-admin");
            return mock(Result.class);
        });

        filter.invoke(invoker, invocation);
        assertThat(RpcContext.getClientAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY)).isNull();
    }

    @Test
    void bootstrapInvocationUsesOnlyScopedBootstrapAssertion() throws Exception {
        DelegationAssertionSigner signer = new DelegationAssertionSigner();
        ReflectionTestUtils.setField(signer, "bootstrapSecret", SECRET);
        assertThat(signer.issueForBootstrap("backend-auth")).isNull();
        ReflectionTestUtils.setField(signer, "developmentBootstrapEnabled", true);
        ReflectionTestUtils.setField(signer, "issuer", DelegationAssertionContract.ISSUER);
        ReflectionTestUtils.setField(signer, "ttlSeconds", 30L);

        DelegationAssertionConsumerFilter filter = new DelegationAssertionConsumerFilter();
        filter.setDelegationAssertionSigner(signer);
        Invoker<?> invoker = mock(Invoker.class);
        URL target = mock(URL.class);
        when(target.getParameter("application")).thenReturn("backend-auth");
        when(invoker.getUrl()).thenReturn(target);
        Invocation invocation = mock(Invocation.class);
        com.ulticode.auth.api.command.CreateAccountCommand command =
                new com.ulticode.auth.api.command.CreateAccountCommand(
                        "bootstrap-command",
                        com.ulticode.common.tracing.IdMetadata.mint(),
                        new com.ulticode.auth.api.command.ActorDelegation(
                                "BOOTSTRAP", "bootstrap", "bootstrap", "one-shot"),
                        com.ulticode.common.tracing.TraceMetadata.EMPTY,
                        "bootstrap-user", "bootstrap@example.com", "Strong-password-123!", "ADMIN");
        when(invocation.getMethodName()).thenReturn("createAccount");
        when(invocation.getArguments()).thenReturn(new Object[] {command});
        when(invoker.invoke(any())).thenAnswer(ignored -> {
            String assertion = RpcContext.getClientAttachment().getAttachment(
                    DelegationAssertionContract.ATTACHMENT_KEY);
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .requireIssuer(DelegationAssertionContract.ISSUER)
                    .requireAudience("backend-auth")
                    .build()
                    .parseSignedClaims(assertion)
                    .getPayload();
            assertThat(claims.getSubject()).isEqualTo("bootstrap");
            assertThat(claims.get(DelegationAssertionContract.ACTOR_TYPE_CLAIM, String.class))
                    .isEqualTo("BOOTSTRAP");
            assertThat(claims.get(DelegationAssertionContract.BOOTSTRAP_CLAIM, Boolean.class))
                    .isTrue();
            return mock(Result.class);
        });

        filter.invoke(invoker, invocation);
        assertThat(RpcContext.getClientAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY)).isNull();
    }
}
