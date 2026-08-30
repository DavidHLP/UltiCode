package com.ulticode.admin.security;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.dubbo.common.URL;
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
    private static final String KEY_ID = "admin-delegation-v1";
    private static final String BOOTSTRAP_KEY_ID = "bootstrap-delegation-v1";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RpcContext.getClientAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void filterIsRegisteredAsDubboSpiAndEmitsRs256Assertion() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(DESCRIPTOR)) {
            assertThat(stream).isNotNull();
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(descriptor).contains(
                    "delegationAssertionConsumer=" + DelegationAssertionConsumerFilter.class.getName());
        }

        KeyPair keyPair = rsaKeyPair();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin-1", "",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        DelegationAssertionSigner signer = new DelegationAssertionSigner();
        ReflectionTestUtils.setField(signer, "privateKeyBase64", encode(keyPair.getPrivate()));
        ReflectionTestUtils.setField(signer, "keyId", KEY_ID);
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
            Jws<Claims> signed = Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .requireIssuer(DelegationAssertionContract.ISSUER)
                    .requireAudience(DelegationAssertionContract.AUDIENCE)
                    .build()
                    .parseSignedClaims(assertion);
            assertThat(signed.getHeader().getAlgorithm()).isEqualTo("RS256");
            assertThat(signed.getHeader().getKeyId()).isEqualTo(KEY_ID);
            assertThat(signed.getPayload().getSubject()).isEqualTo("admin-1");
            assertThat(signed.getPayload().getId()).isNotBlank();
            assertThat(signed.getPayload().get(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, String.class))
                    .isEqualTo("backend-admin");
            return mock(Result.class);
        });

        filter.invoke(invoker, invocation);
        assertThat(RpcContext.getClientAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY)).isNull();
    }

    @Test
    void bootstrapInvocationUsesOnlySeparateScopedRs256Key() throws Exception {
        KeyPair bootstrapKeyPair = rsaKeyPair();
        DelegationAssertionSigner signer = new DelegationAssertionSigner();
        ReflectionTestUtils.setField(signer, "bootstrapPrivateKeyBase64", encode(bootstrapKeyPair.getPrivate()));
        ReflectionTestUtils.setField(signer, "bootstrapKeyId", BOOTSTRAP_KEY_ID);
        assertThat(signer.issueForBootstrap("backend-auth")).isNull();
        ReflectionTestUtils.setField(signer, "developmentBootstrapEnabled", true);
        ReflectionTestUtils.setField(signer, "issuer", DelegationAssertionContract.ISSUER);
        ReflectionTestUtils.setField(signer, "ttlSeconds", 30L);

        DelegationAssertionConsumerFilter filter = new DelegationAssertionConsumerFilter();
        filter.setDelegationAssertionSigner(signer);
        Invoker<?> invoker = mock(Invoker.class);
        URL target = mock(URL.class);
        when(target.getParameter("application")).thenReturn("backend-admin");
        when(target.getParameter("group")).thenReturn("backend-auth");
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
            Jws<Claims> signed = Jwts.parser()
                    .verifyWith(bootstrapKeyPair.getPublic())
                    .requireIssuer(DelegationAssertionContract.ISSUER)
                    .requireAudience("backend-auth")
                    .build()
                    .parseSignedClaims(assertion);
            assertThat(signed.getHeader().getAlgorithm()).isEqualTo("RS256");
            assertThat(signed.getHeader().getKeyId()).isEqualTo(BOOTSTRAP_KEY_ID);
            assertThat(signed.getPayload().getSubject()).isEqualTo("bootstrap");
            assertThat(signed.getPayload().get(DelegationAssertionContract.ACTOR_TYPE_CLAIM, String.class))
                    .isEqualTo("BOOTSTRAP");
            assertThat(signed.getPayload().get(DelegationAssertionContract.BOOTSTRAP_CLAIM, Boolean.class))
                    .isTrue();
            return mock(Result.class);
        });

        filter.invoke(invoker, invocation);
        assertThat(RpcContext.getClientAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY)).isNull();
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String encode(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
