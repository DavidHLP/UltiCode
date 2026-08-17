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
        ReflectionTestUtils.setField(signer, "audience", DelegationAssertionContract.AUDIENCE);
        ReflectionTestUtils.setField(signer, "ttlSeconds", 30L);

        Filter filter = getExtensionLoader(Filter.class)
                .getExtension("delegationAssertionConsumer");
        assertThat(filter).isInstanceOf(DelegationAssertionConsumerFilter.class);
        ((DelegationAssertionConsumerFilter) filter).setDelegationAssertionSigner(signer);

        Invoker<?> invoker = mock(Invoker.class);
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
}
