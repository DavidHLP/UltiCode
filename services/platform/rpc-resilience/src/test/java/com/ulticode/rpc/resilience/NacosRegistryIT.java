package com.ulticode.rpc.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Run only through scripts/test/nacos-client-integration.sh, never against an existing registry. */
class NacosRegistryIT {

    private static final String SERVICE = "backend-auth";
    private static final String GROUP = "DEFAULT_GROUP";
    private static final String DATA_ID = "com.ulticode.auth.ProtobufFixture";
    private static final String CONFIG_GROUP = "mapping";
    private static final Duration WAIT = Duration.ofSeconds(60);
    private static String phase = "startup";

    public static void main(String[] args) throws Exception {
        NacosRuntimeDependencyTest.requiresSafeGencodeWithoutAnUnsafeOverride();
        assertThat(NacosFactory.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith("nacos-api-patched-2.5.1-ulticode-1.jar");
        try {
            new NacosRegistryIT().authenticatedRegistrySurvivesServerRestart();
        } catch (Throwable failure) {
            Files.writeString(Path.of(required("NACOS_FIXTURE_CONTROL"), "failure"), failureDiagnostic(failure));
            throw failure;
        }
        System.out.println("PASS: authenticated naming/config, auth rejection, reconnect/resubscribe and shutdown");
    }

    @Test
    void authenticatedRegistrySurvivesServerRestart() throws Exception {
        NacosRuntimeDependencyTest.requiresSafeGencodeWithoutAnUnsafeOverride();
        NacosRuntimeDependencyTest.assertSinglePatchedApi();
        Path control = Path.of(required("NACOS_FIXTURE_CONTROL"));
        assertThat(Files.isDirectory(control)).isTrue();
        assertThat(required("NACOS_FIXTURE_SERVER")).matches("127\\.0\\.0\\.1:[0-9]{2,5}");
        BlockingQueue<List<Instance>> namingEvents = new LinkedBlockingQueue<>();
        BlockingQueue<String> configEvents = new LinkedBlockingQueue<>();
        EventListener namingListener = event -> namingEvents.add(((NamingEvent) event).getInstances());
        Listener configListener = new Listener() {
            public Executor getExecutor() { return null; }
            public void receiveConfigInfo(String value) { configEvents.add(value); }
        };
        phase = "initial-transport";
        try (Clients clients = new Clients()) {
            NamingService producer = clients.naming(properties(true, false));
            NamingService consumer = clients.naming(properties(true, false));
            ConfigService config = clients.config(properties(true, false));
            // RpcClient.start() may return while asynchronous connection setup
            // continues. HTTP container health does not prove client transport
            // readiness; never reinterpret a disconnected call as an auth result.
            awaitConnected(producer::getServerStatus, consumer::getServerStatus, config::getServerStatus);
            phase = "authenticated-naming";
            consumer.subscribe(SERVICE, GROUP, namingListener);
            Instance initial = instance(20880, "before-restart");
            producer.registerInstance(SERVICE, GROUP, initial);
            await("authenticated discovery", () -> hasInstance(consumer.getAllInstances(SERVICE, GROUP, false), 20880));
            awaitEvent(namingEvents, instances -> hasInstance(instances, 20880), "initial naming callback");
            assertThat(consumer.getAllInstances(SERVICE, GROUP, false)).anySatisfy(instance ->
                    assertThat(instance.getMetadata()).containsEntry("protobuf-fixture", "before-restart"));

            phase = "authenticated-config";
            assertThat(config.publishConfig(DATA_ID, CONFIG_GROUP, "metadata-v1")).isTrue();
            await("config publication", () -> "metadata-v1".equals(config.getConfig(DATA_ID, CONFIG_GROUP, 5000)));
            assertThat(config.getConfigAndSignListener(DATA_ID, CONFIG_GROUP, 5000, configListener))
                    .isEqualTo("metadata-v1");
            assertThat(config.publishConfig(DATA_ID, CONFIG_GROUP, "metadata-v2")).isTrue();
            awaitEvent(configEvents, "metadata-v2"::equals, "initial config callback");

            phase = "missing-auth-rejection";
            assertRejected(false, false);
            phase = "wrong-auth-rejection";
            assertRejected(true, true);
            phase = "observe-disconnect";
            Files.createFile(control.resolve("ready-for-outage"));
            await("server stopped marker", () -> Files.exists(control.resolve("server-stopped")));
            await("naming/config clients observe disconnect", () -> !"UP".equals(producer.getServerStatus())
                    && !"UP".equals(consumer.getServerStatus()) && !"UP".equals(config.getServerStatus()));
            Files.createFile(control.resolve("observed-disconnect"));
            await("server restarted marker", () -> Files.exists(control.resolve("server-restarted")));
            phase = "automatic-recovery";
            await("original clients reconnect", () -> "UP".equals(producer.getServerStatus())
                    && "UP".equals(consumer.getServerStatus()) && "UP".equals(config.getServerStatus()));
            // No manual register or subscribe calls on recovery: the original
            // producer must redo its ephemeral registration after the server lost it.
            await("automatic re-registration", () -> hasInstance(consumer.getAllInstances(SERVICE, GROUP, false), 20880));
            assertThat(config.getConfig(DATA_ID, CONFIG_GROUP, 5000)).isEqualTo("metadata-v2");
            phase = "subscription-recovery";
            namingEvents.clear();
            configEvents.clear();
            Instance recovered = instance(20881, "after-restart");
            producer.registerInstance(SERVICE, GROUP, recovered);
            awaitEvent(namingEvents, instances -> hasInstance(instances, 20881), "recovered naming subscription");
            assertThat(config.publishConfig(DATA_ID, CONFIG_GROUP, "metadata-v3")).isTrue();
            awaitEvent(configEvents, "metadata-v3"::equals, "recovered config subscription");

            phase = "clean-shutdown";
            producer.deregisterInstance(SERVICE, GROUP, initial);
            producer.deregisterInstance(SERVICE, GROUP, recovered);
            await("deregistration", () -> consumer.getAllInstances(SERVICE, GROUP, false).isEmpty());
            consumer.unsubscribe(SERVICE, GROUP, namingListener);
            config.removeListener(DATA_ID, CONFIG_GROUP, configListener);
            assertThat(config.removeConfig(DATA_ID, CONFIG_GROUP)).isTrue();
            clients.close();
            assertThat(producer.getServerStatus()).isEqualTo("DOWN");
            assertThat(consumer.getServerStatus()).isEqualTo("DOWN");
            assertThat(config.getServerStatus()).isEqualTo("DOWN");
        }
    }

    private static void assertRejected(boolean authenticated, boolean wrongPassword) {
        // An arbitrary error (timeout, missing service, serialization failure)
        // is NOT evidence of authentication enforcement. Require Nacos NO_RIGHT.
        assertThatThrownBy(() -> {
            try (Clients clients = new Clients()) {
                NamingService naming = clients.naming(properties(authenticated, wrongPassword));
                awaitConnected(naming::getServerStatus);
                naming.registerInstance(SERVICE, GROUP, instance(20882, "must-be-rejected"));
            }
        }).isInstanceOfSatisfying(NacosException.class,
                failure -> assertThat(failure.getErrCode()).isEqualTo(NacosException.NO_RIGHT));
        assertThatThrownBy(() -> {
            try (Clients clients = new Clients()) {
                ConfigService config = clients.config(properties(authenticated, wrongPassword));
                awaitConnected(config::getServerStatus);
                config.getConfig(DATA_ID, CONFIG_GROUP, 5000);
            }
        }).isInstanceOfSatisfying(NacosException.class,
                failure -> assertThat(failure.getErrCode()).isEqualTo(NacosException.NO_RIGHT));
    }

    static String failureDiagnostic(Throwable failure) {
        // Never export SDK exception messages or request values. Those can
        // contain credentials; numeric codes and these fixed labels are safe.
        StringBuilder diagnostic = new StringBuilder("phase=" + phase + "\n");
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            diagnostic.append("exception=").append(cause.getClass().getName()).append('\n');
            if (cause instanceof NacosException nacos) {
                diagnostic.append("nacos-error-code=").append(nacos.getErrCode()).append('\n');
                String classification = switch (nacos.getErrCode()) {
                    case NacosException.CLIENT_DISCONNECT -> "client-disconnected";
                    case NacosException.NO_RIGHT -> "authorization-rejected";
                    case NacosException.INVALID_SERVER_STATUS -> "server-not-ready";
                    case NacosException.NOT_FOUND, NacosException.RESOURCE_NOT_FOUND -> "resource-not-found";
                    default -> "nacos-request-failed";
                };
                diagnostic.append("classification=").append(classification).append('\n');
            }
            for (StackTraceElement frame : cause.getStackTrace()) {
                if (frame.getClassName().equals(NacosRegistryIT.class.getName())) {
                    diagnostic.append("line=").append(frame.getLineNumber()).append('\n');
                }
            }
        }
        return diagnostic.toString();
    }

    @SafeVarargs
    static void awaitConnected(Supplier<String>... statuses) throws Exception {
        await("client transport readiness", () -> {
            for (Supplier<String> status : statuses) {
                if (!"UP".equals(status.get())) {
                    return false;
                }
            }
            return true;
        });
    }

    private static Properties properties(boolean authenticated, boolean wrongPassword) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, required("NACOS_FIXTURE_SERVER"));
        properties.setProperty(PropertyKeyConst.NAMESPACE, required("NACOS_FIXTURE_NAMESPACE"));
        properties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "false");
        properties.setProperty(PropertyKeyConst.LOG_ALL_PROPERTIES, "false");
        if (authenticated) {
            properties.setProperty(PropertyKeyConst.USERNAME, required("AUTH_NACOS_USERNAME"));
            properties.setProperty(PropertyKeyConst.PASSWORD,
                    required(wrongPassword ? "NACOS_FIXTURE_WRONG_PASSWORD" : "AUTH_NACOS_PASSWORD"));
        }
        return properties;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as("fixture variable %s must be set by the disposable integration script", name).isNotEmpty();
        return value;
    }

    private static Instance instance(int port, String phase) {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(port);
        instance.setEphemeral(true);
        instance.setMetadata(Map.of("protobuf-fixture", phase));
        return instance;
    }

    private static boolean hasInstance(List<Instance> instances, int port) {
        return instances.stream().anyMatch(instance -> instance.getPort() == port && instance.isHealthy());
    }

    private static <T> void awaitEvent(BlockingQueue<T> events, Predicate<T> matches, String label) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            T event = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (event != null && matches.test(event)) {
                return;
            }
        }
        throw new AssertionError("Timed out waiting for " + label);
    }

    private static void await(String label, CheckedCondition condition) throws Exception {
        await(label, condition, WAIT);
    }

    static void await(String label, CheckedCondition condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for " + label);
    }

    @FunctionalInterface
    interface CheckedCondition {
        boolean matches() throws Exception;
    }

    private static final class Clients implements AutoCloseable {
        private final Deque<AutoCloseable> resources = new ArrayDeque<>();

        NamingService naming(Properties properties) throws NacosException {
            NamingService service = NacosFactory.createNamingService(properties);
            resources.push(service::shutDown);
            return service;
        }

        ConfigService config(Properties properties) throws NacosException {
            ConfigService service = NacosFactory.createConfigService(properties);
            resources.push(service::shutDown);
            return service;
        }

        public void close() throws Exception {
            Exception failure = null;
            while (!resources.isEmpty()) {
                try {
                    resources.pop().close();
                } catch (Exception error) {
                    if (failure == null) { failure = error; } else { failure.addSuppressed(error); }
                }
            }
            if (failure != null) { throw failure; }
        }
    }
}
