package com.ulticode.rpc.resilience;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcContext;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Requires the peer certificate to match the configured owner caller policy.
 *
 * <p>Dubbo 3.3.6 exposes the Triple request through {@link RpcContext}, but
 * does not expose the Netty SSL session on that request. The bounded extractor
 * follows Dubbo's own request/channel wrappers to the transport attribute; it
 * never trusts an invocation attachment or remote application name.
 */
@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER}, order = -1000)
public final class DubboMtlsIdentityFilter implements Filter {

    private final Configuration configuration;
    private final Clock clock;

    public DubboMtlsIdentityFilter() {
        this(Configuration.fromEnvironment(), Clock.systemUTC());
    }

    DubboMtlsIdentityFilter(Configuration configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (!configuration.enabled()) {
            return invoker.invoke(invocation);
        }
        try {
            if (RpcContext.getServiceContext().isConsumerSide()) {
                String targetService = invoker.getUrl() == null
                        ? null : invoker.getUrl().getGroup();
                DubboMtlsCertificatePolicy.authorizeTarget(
                        configuration.serviceIdentity(),
                        DubboMtlsCertificatePolicy.expectedTargets(configuration.serviceIdentity()),
                        targetService,
                        SslSessionExtractor.peerCertificate(invoker),
                        Instant.now(clock));
            } else {
                DubboMtlsCertificatePolicy.authorize(
                        configuration.serviceIdentity(), configuration.allowedCallers(),
                        SslSessionExtractor.peerCertificate(
                                RpcContext.getServiceContext().getRequest()),
                        Instant.now(clock));
            }
        } catch (CertificateException | SSLPeerUnverifiedException error) {
            throw new RpcException(
                    RpcException.AUTHORIZATION_EXCEPTION,
                    "Dubbo mTLS peer certificate rejected",
                    error);
        }
        return invoker.invoke(invocation);
    }

    record Configuration(boolean enabled, String serviceIdentity, Set<String> allowedCallers) {
        Configuration {
            allowedCallers = Set.copyOf(allowedCallers);
            if (enabled) {
                if (serviceIdentity == null || serviceIdentity.isBlank()) {
                    throw new IllegalArgumentException("DUBBO_MTLS_SERVICE_IDENTITY is required");
                }
                DubboMtlsCertificatePolicy.validateConfiguration(serviceIdentity, allowedCallers);
            }
        }

        static Configuration fromEnvironment() {
            Map<String, String> environment = System.getenv();
            boolean enabled = Boolean.parseBoolean(environment.getOrDefault(
                    "DUBBO_MTLS_ENABLED", "false"));
            if (!enabled) {
                return new Configuration(false, "", Set.of());
            }
            String serviceIdentity = environment.getOrDefault("DUBBO_MTLS_SERVICE_IDENTITY", "");
            Set<String> allowedCallers = parseCallers(
                    environment.getOrDefault("DUBBO_MTLS_ALLOWED_CALLERS", ""));
            return new Configuration(true, serviceIdentity, allowedCallers);
        }

        private static Set<String> parseCallers(String value) {
            if (value.isBlank()) {
                return Set.of();
            }
            Set<String> callers = new HashSet<>();
            for (String caller : value.split(",")) {
                String normalized = caller.trim();
                if (normalized.isBlank()) {
                    throw new IllegalArgumentException("DUBBO_MTLS_ALLOWED_CALLERS contains an empty identity");
                }
                callers.add(normalized);
            }
            return callers;
        }
    }

    private static final class SslSessionExtractor {
        private static final String SSL_SESSION_ATTRIBUTE = "ssl-session";
        private static final String NETTY_CHANNEL = "io.netty.channel.Channel";
        private static final String NETTY_ATTRIBUTE_KEY = "io.netty.util.AttributeKey";
        private static final int MAX_DEPTH = 4;

        private SslSessionExtractor() {
        }

        static X509Certificate peerCertificate(Object request)
                throws CertificateException, SSLPeerUnverifiedException {
            SSLSession session = findSession(request, new IdentityHashMap<>(), 0);
            if (session == null) {
                throw new CertificateException("Dubbo mTLS SSL session is missing");
            }
            java.security.cert.Certificate[] certificates = session.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) {
                throw new CertificateException("Dubbo mTLS peer certificate is not X509");
            }
            return certificate;
        }

        private static SSLSession findSession(
                Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
            if (value == null || depth > MAX_DEPTH || visited.put(value, Boolean.TRUE) != null) {
                return null;
            }
            if (value instanceof SSLSession session) {
                return session;
            }
            if (value instanceof Channel channel) {
                return sessionFromDubboChannel(channel);
            }

            String className = value.getClass().getName();
            if (className.equals("org.apache.dubbo.rpc.protocol.tri.TripleInvoker")) {
                return sessionFromTripleInvoker(value, visited, depth);
            }
            if (className.startsWith("io.netty.")) {
                SSLSession session = sessionFromNettyChannel(value);
                if (session != null) {
                    return session;
                }
                return findSession(invokeNoArg(value, "parent"), visited, depth + 1);
            }
            if (!className.startsWith("org.apache.dubbo.remoting.http12")) {
                return null;
            }
            for (Class<?> current = value.getClass(); current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!field.getName().equals("channel")
                            && !field.getName().equals("http2StreamChannel")
                            && !field.getName().equals("h2StreamChannel")) {
                        continue;
                    }
                    try {
                        if (!field.trySetAccessible()) {
                            continue;
                        }
                        SSLSession session = findSession(field.get(value), visited, depth + 1);
                        if (session != null) {
                            return session;
                        }
                    } catch (IllegalAccessException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }

        private static SSLSession sessionFromTripleInvoker(
                Object invoker, IdentityHashMap<Object, Boolean> visited, int depth) {
            Object connectionClient = fieldValue(invoker, "connectionClient");
            if (connectionClient == null) {
                return null;
            }
            return findSession(invokeClientChannel(connectionClient), visited, depth + 1);
        }

        private static Object fieldValue(Object value, String fieldName) {
            try {
                for (Class<?> current = value.getClass();
                        current != null; current = current.getSuperclass()) {
                    for (Field field : current.getDeclaredFields()) {
                        if (field.getName().equals(fieldName) && field.trySetAccessible()) {
                            return field.get(value);
                        }
                    }
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                return null;
            }
            return null;
        }

        private static Object invokeClientChannel(Object value) {
            try {
                Method method = value.getClass().getMethod("getChannel", Boolean.class);
                return method.invoke(value, Boolean.TRUE);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        private static SSLSession sessionFromDubboChannel(Channel channel) {
            Object value = channel.getAttribute(SSL_SESSION_ATTRIBUTE);
            return value instanceof SSLSession session ? session : null;
        }

        private static SSLSession sessionFromNettyChannel(Object channel) {
            try {
                Class<?> channelType = Class.forName(NETTY_CHANNEL);
                Class<?> attributeKeyType = Class.forName(NETTY_ATTRIBUTE_KEY);
                Method valueOf = attributeKeyType.getMethod("valueOf", String.class);
                Object key = valueOf.invoke(null, SSL_SESSION_ATTRIBUTE);
                Method attr = channelType.getMethod("attr", attributeKeyType);
                Object attribute = attr.invoke(channel, key);
                Object value = attribute.getClass().getMethod("get").invoke(attribute);
                return value instanceof SSLSession session ? session : null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        private static Object invokeNoArg(Object value, String methodName) {
            try {
                Method method = Class.forName(NETTY_CHANNEL).getMethod(methodName);
                return method.invoke(value);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }
}
