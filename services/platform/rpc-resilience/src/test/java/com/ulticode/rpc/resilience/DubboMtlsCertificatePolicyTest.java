package com.ulticode.rpc.resilience;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLSession;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.Test;


class DubboMtlsCertificatePolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    @Test
    void providerFilterIsRegisteredAsDubboSpi() throws Exception {
        try (InputStream descriptor = getClass().getClassLoader().getResourceAsStream(
                "META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter")) {
            assertThat(descriptor).isNotNull();
            assertThat(new String(descriptor.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("workload-mtls=" + DubboMtlsIdentityFilter.class.getName());
        }
        assertThat(getExtensionLoader(Filter.class).getExtension("workload-mtls"))
                .isInstanceOf(DubboMtlsIdentityFilter.class);
    }

    @Test
    void providerFilterReadsThePeerCertificateFromDubboTransport() throws Exception {
        SSLSession session = proxy(SSLSession.class, (proxy, method, args) -> {
            if (method.getName().equals("getPeerCertificates")) {
                return new X509Certificate[]{
                        certificate(List.of(List.of(6, "spiffe://ulticode/service/backend-admin")))};
            }
            return null;
        });
        Channel channel = proxy(Channel.class, (proxy, method, args) ->
                method.getName().equals("getAttribute")
                        && "ssl-session".equals(args[0]) ? session : null);
        Result result = proxy(Result.class, (proxy, method, args) -> null);
        Invoker<?> invoker = proxy(Invoker.class,
                (proxy, method, args) -> method.getName().equals("invoke") ? result : null);
        Invocation invocation = proxy(Invocation.class, (proxy, method, args) -> null);
        RpcContext.getServiceContext().setUrl(
                URL.valueOf("dubbo://127.0.0.1:20880?side=provider"));
        RpcContext.getServiceContext().setRequest(channel);
        try {
            DubboMtlsIdentityFilter filter = new DubboMtlsIdentityFilter(
                    new DubboMtlsIdentityFilter.Configuration(
                            true, "backend-app",
                            DubboMtlsCertificatePolicy.expectedCallers("backend-app")),
                    Clock.fixed(NOW, java.time.ZoneOffset.UTC));

            assertThat(filter.invoke(invoker, invocation)).isSameAs(result);
        } finally {
            RpcContext.removeServiceContext();
        }
    }

    @Test
    void acceptsTheAllowedSpiffeIdentity() throws CertificateException {
        X509Certificate certificate = certificate(
                List.of(List.of(6, "spiffe://ulticode/service/backend-admin")));

        assertThat(DubboMtlsCertificatePolicy.authorize(
                "backend-app",
                DubboMtlsCertificatePolicy.expectedCallers("backend-app"),
                certificate,
                NOW)).isEqualTo("backend-admin");
    }

    @Test
    void rejectsUnknownExpiredAndWrongSanCertificates() {
        assertRejected("spiffe://ulticode/service/backend-search", false,
                "unknown workload identity");
        assertRejected("spiffe://ulticode/service/backend-admin/extra", false,
                "invalid SPIFFE SAN");
        assertRejected("spiffe://ulticode/service/backend-admin", true,
                "expired");
    }

    @Test
    void rejectsCallerThatIsNotInTheTargetAudience() {
        X509Certificate certificate = certificate(
                List.of(List.of(6, "spiffe://ulticode/service/backend-judge")));

        assertThatThrownBy(() -> DubboMtlsCertificatePolicy.authorize(
                "backend-auth",
                DubboMtlsCertificatePolicy.expectedCallers("backend-auth"),
                certificate,
                NOW)).isInstanceOf(CertificateException.class)
                .hasMessage("Dubbo mTLS caller is not authorized");
    }

    @Test
    void rejectsAProviderCertificateForTheWrongConsumerTarget() {
        X509Certificate certificate = certificate(
                List.of(List.of(6, "spiffe://ulticode/service/backend-admin")));

        assertThatThrownBy(() -> DubboMtlsCertificatePolicy.authorizeTarget(
                "backend-app",
                DubboMtlsCertificatePolicy.expectedTargets("backend-app"),
                "backend-submission",
                certificate,
                NOW)).isInstanceOf(CertificateException.class)
                .hasMessage("Dubbo mTLS server identity does not match target");
    }

    @Test
    void rejectsAChangedCallerPolicyBeforeInvocation() {
        assertThatThrownBy(() -> DubboMtlsCertificatePolicy.validateConfiguration(
                "backend-auth", Set.of("backend-admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match backend-auth");
    }

    private static <T> T proxy(
            Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void assertRejected(String san, boolean expired, String message) {
        X509Certificate certificate = certificate(List.of(List.of(6, san)), expired);
        assertThatThrownBy(() -> DubboMtlsCertificatePolicy.authorize(
                "backend-app",
                DubboMtlsCertificatePolicy.expectedCallers("backend-app"),
                certificate,
                NOW)).isInstanceOf(CertificateException.class)
                .hasMessageContaining(message);
    }

    private static X509Certificate certificate(Collection<List<?>> sans) {
        return certificate(sans, false);
    }

    private static X509Certificate certificate(Collection<List<?>> sans, boolean expired) {
        return new StubCertificate(sans, expired);
    }

    private static final class StubCertificate extends X509Certificate {
        private final Collection<List<?>> sans;
        private final boolean expired;

        private StubCertificate(Collection<List<?>> sans, boolean expired) {
            this.sans = sans;
            this.expired = expired;
        }

        @Override
        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
            checkValidity(new Date());
        }

        @Override
        public void checkValidity(Date date)
                throws CertificateExpiredException, CertificateNotYetValidException {
            if (expired) {
                throw new CertificateExpiredException("expired");
            }
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() {
            return sans;
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return () -> "issuer";
        }

        @Override
        public Principal getSubjectDN() {
            return () -> "subject";
        }

        @Override
        public Date getNotBefore() {
            return Date.from(Instant.EPOCH);
        }

        @Override
        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "none";
        }

        @Override
        public String getSigAlgOID() {
            return "0.0";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public Collection<List<?>> getIssuerAlternativeNames() {
            return null;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public void verify(PublicKey key, java.security.Provider sigProvider) {
        }

        @Override
        public String toString() {
            return "stub";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }
    }
}
