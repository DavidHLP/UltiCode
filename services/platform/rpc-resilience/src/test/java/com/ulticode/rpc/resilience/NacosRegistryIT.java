package com.ulticode.rpc.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigTransportClient;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import io.grpc.ManagedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Run only through scripts/test/nacos-client-integration.sh, never against an existing registry. */
class NacosRegistryIT {

    private static final String SERVICE = "backend-auth";
    private static final String GROUP = "DEFAULT_GROUP";
    private static final String DATA_ID = "com.ulticode.auth.ProtobufFixture";
    private static final String CONFIG_GROUP = "mapping";
    private static final String FIXTURE_DATA_ID = "protobuf-fixture-metadata";
    private static final String FIXTURE_CONFIG_GROUP = "FIXTURE_CONFIG";
    private static final String WORKLOAD_CONTENT = "workload-metadata-v1";
    private static final Duration WAIT = Duration.ofSeconds(60);
    private static String phase = "startup";
    private static String callbackReadback = "not-run";

    public static void main(String[] args) throws Exception {
        NacosRuntimeDependencyTest.requiresSafeGencodeWithoutAnUnsafeOverride();
        assertThat(NacosFactory.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith("nacos-api-patched-2.5.3-ulticode-1.jar");
        try {
            new NacosRegistryIT().authenticatedRegistrySurvivesServerRestart();
        } catch (Throwable failure) {
            Files.writeString(Path.of(required("NACOS_FIXTURE_CONTROL"), "failure"), failureDiagnostic(failure));
            throw failure;
        }
        System.out.println("PASS: isolated client compatibility and declared-resource closure; "
                + "original workload batch-listen (403) and full SDK lifecycle boundaries remain unresolved");
    }

    @Test
    void authenticatedRegistrySurvivesServerRestart() throws Exception {
        NacosRuntimeDependencyTest.requiresSafeGencodeWithoutAnUnsafeOverride();
        NacosRuntimeDependencyTest.assertPinnedNacosRuntime();
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
            ConfigService workloadConfig = clients.config(properties(true, false));
            ConfigService config = clients.config(configProperties(true, false));
            // RpcClient.start() may return while asynchronous connection setup
            // continues. HTTP container health does not prove client transport
            // readiness; never reinterpret a disconnected call as an auth result.
            awaitConnected(producer::getServerStatus, consumer::getServerStatus,
                    workloadConfig::getServerStatus, config::getServerStatus);
            phase = "authenticated-naming";
            consumer.subscribe(SERVICE, GROUP, namingListener);
            Instance initial = instance(20880, "before-restart");
            producer.registerInstance(SERVICE, GROUP, initial);
            await("authenticated discovery", () -> hasInstance(consumer.getAllInstances(SERVICE, GROUP, false), 20880));
            awaitEvent(namingEvents, instances -> hasInstance(instances, 20880), "initial naming callback");
            assertThat(consumer.getAllInstances(SERVICE, GROUP, false)).anySatisfy(instance ->
                    assertThat(instance.getMetadata()).containsEntry("protobuf-fixture", "before-restart"));

            // The unchanged production AUTH grants allow these direct calls,
            // but server 2.3.2 authorizes batch listen as namespace:*:config/*.
            // Require the actual numeric denial, not an absent callback.
            phase = "workload-direct-config";
            assertThat(workloadConfig.publishConfig(DATA_ID, CONFIG_GROUP, WORKLOAD_CONTENT)).isTrue();
            await("workload config publication",
                    () -> WORKLOAD_CONTENT.equals(workloadConfig.getConfig(DATA_ID, CONFIG_GROUP, 5000)));
            phase = "workload-batch-denial";
            Response batch = requestOnConfigTransport(workloadConfig,
                    workloadBatchRequest(required("NACOS_FIXTURE_NAMESPACE")));
            Files.writeString(control.resolve("evidence"), batchResponseDiagnostic(batch));
            requireNoRightResponse(batch);

            // Positive protocol coverage uses a separate synthetic-only namespace
            // and principal. Its namespace-wide read is NOT a production ACL fix.
            phase = "isolated-config";
            assertThat(config.publishConfig(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, "metadata-v1")).isTrue();
            await("isolated config publication", () -> "metadata-v1".equals(
                    config.getConfig(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, 5000)));
            assertThat(config.getConfigAndSignListener(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, 5000, configListener))
                    .isEqualTo("metadata-v1");
            assertThat(config.publishConfig(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, "metadata-v2")).isTrue();
            awaitConfigEvent(config, configEvents, "metadata-v2", "initial isolated config callback");

            phase = "isolated-config-scope";
            Properties outsideFixture = configProperties(true, false);
            outsideFixture.setProperty(PropertyKeyConst.NAMESPACE, required("NACOS_FIXTURE_NAMESPACE"));
            assertConfigReadRejected(outsideFixture, DATA_ID, CONFIG_GROUP);
            Properties outsideWorkload = properties(true, false);
            outsideWorkload.setProperty(PropertyKeyConst.NAMESPACE, required("NACOS_FIXTURE_CONFIG_NAMESPACE"));
            assertConfigReadRejected(outsideWorkload, FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP);
            // A false publish result alone could be a transport failure. Inspect
            // the same standard request's numeric response for exact-write ACL.
            requireNoRightResponse(requestOnConfigTransport(config, new ConfigPublishRequest(
                    FIXTURE_DATA_ID + "-other", FIXTURE_CONFIG_GROUP,
                    required("NACOS_FIXTURE_CONFIG_NAMESPACE"), "must-be-rejected")));

            phase = "missing-auth-rejection";
            assertRejected(false, false);
            phase = "wrong-auth-rejection";
            assertRejected(true, true);
            phase = "observe-disconnect";
            Files.createFile(control.resolve("ready-for-outage"));
            await("server stopped marker", () -> Files.exists(control.resolve("server-stopped")));
            await("naming/config clients observe disconnect", () -> !"UP".equals(producer.getServerStatus())
                    && !"UP".equals(consumer.getServerStatus()) && !"UP".equals(workloadConfig.getServerStatus())
                    && !"UP".equals(config.getServerStatus()));
            Files.createFile(control.resolve("observed-disconnect"));
            await("server restarted marker", () -> Files.exists(control.resolve("server-restarted")));
            phase = "automatic-recovery";
            await("original clients reconnect", () -> "UP".equals(producer.getServerStatus())
                    && "UP".equals(consumer.getServerStatus()) && "UP".equals(workloadConfig.getServerStatus())
                    && "UP".equals(config.getServerStatus()));
            // No manual register or subscribe calls on recovery: the original
            // producer must redo its ephemeral registration after the server lost it.
            await("automatic re-registration", () -> hasInstance(consumer.getAllInstances(SERVICE, GROUP, false), 20880));
            assertThat(workloadConfig.getConfig(DATA_ID, CONFIG_GROUP, 5000)).isEqualTo(WORKLOAD_CONTENT);
            assertThat(config.getConfig(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, 5000)).isEqualTo("metadata-v2");
            phase = "subscription-recovery";
            namingEvents.clear();
            configEvents.clear();
            Instance recovered = instance(20881, "after-restart");
            producer.registerInstance(SERVICE, GROUP, recovered);
            awaitEvent(namingEvents, instances -> hasInstance(instances, 20881), "recovered naming subscription");
            assertThat(config.publishConfig(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, "metadata-v3")).isTrue();
            awaitConfigEvent(config, configEvents, "metadata-v3", "recovered isolated config subscription");

            phase = "clean-shutdown";
            producer.deregisterInstance(SERVICE, GROUP, initial);
            producer.deregisterInstance(SERVICE, GROUP, recovered);
            await("deregistration", () -> consumer.getAllInstances(SERVICE, GROUP, false).isEmpty());
            consumer.unsubscribe(SERVICE, GROUP, namingListener);
            config.removeListener(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP, configListener);
            assertThat(config.removeConfig(FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP)).isTrue();
            assertThat(workloadConfig.removeConfig(DATA_ID, CONFIG_GROUP)).isTrue();
            ConfigShutdown workloadShutdown = ConfigShutdown.capture(workloadConfig);
            ConfigShutdown isolatedShutdown = ConfigShutdown.capture(config);
            clients.close();
            assertThat(producer.getServerStatus()).isEqualTo("DOWN");
            assertThat(consumer.getServerStatus()).isEqualTo("DOWN");
            // Nacos 2.5.3 config getServerStatus() calls ensureRpcClient("0"):
            // after shutdown removes its factory entry, that observer creates
            // a replacement connection. Inspect captured resources instead.
            Files.writeString(control.resolve("evidence"), retainedListenerDiagnostic(
                    workloadShutdown.retainedListenerPools() + isolatedShutdown.retainedListenerPools()),
                    StandardOpenOption.APPEND);
            workloadShutdown.assertClosed(WAIT);
            isolatedShutdown.assertClosed(WAIT);
            Files.writeString(control.resolve("evidence"), "config-declared-resources-closed=true\n",
                    StandardOpenOption.APPEND);
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
        assertConfigReadRejected(properties(authenticated, wrongPassword), DATA_ID, CONFIG_GROUP);
        assertConfigReadRejected(configProperties(authenticated, wrongPassword), FIXTURE_DATA_ID, FIXTURE_CONFIG_GROUP);
    }

    private static void assertConfigReadRejected(Properties properties, String dataId, String group) {
        assertThatThrownBy(() -> {
            try (Clients clients = new Clients()) {
                ConfigService config = clients.config(properties);
                awaitConnected(config::getServerStatus);
                config.getConfig(dataId, group, 5000);
            }
        }).isInstanceOfSatisfying(NacosException.class,
                failure -> assertThat(failure.getErrCode()).isEqualTo(NacosException.NO_RIGHT));
    }

    static ConfigBatchListenRequest workloadBatchRequest(String namespace) throws Exception {
        assertThat(namespace).isEqualTo("protobuf-fixture");
        ConfigBatchListenRequest request = new ConfigBatchListenRequest();
        request.setListen(true);
        String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest(WORKLOAD_CONTENT.getBytes(StandardCharsets.UTF_8)));
        request.addConfigListenContext(CONFIG_GROUP, DATA_ID, namespace, md5);
        return request;
    }

    static void requireNoRightResponse(Response response) {
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(NacosException.NO_RIGHT);
    }

    static String batchResponseDiagnostic(Response response) {
        assertThat(response).isNotNull();
        return "workload-batch-result-code=" + response.getResultCode() + "\n"
                + "workload-batch-error-code=" + response.getErrorCode() + "\n";
    }

    private static ClientWorker configWorker(ConfigService config) throws ReflectiveOperationException {
        return (ClientWorker) field(NacosConfigService.class, "worker", config);
    }

    private static Object field(Class<?> owner, String name, Object target) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static String retainedListenerDiagnostic(long retained) {
        assertThat(retained).isNotNegative();
        return "config-retained-listener-pools=" + retained + "\n";
    }

    record RpcShutdown(RpcClient client, ManagedChannel channel, ExecutorService events, ExecutorService grpc) {
        static RpcShutdown capture(RpcClient client) throws ReflectiveOperationException {
            GrpcConnection connection = (GrpcConnection) field(RpcClient.class, "currentConnection", client);
            assertThat(connection).isNotNull();
            return new RpcShutdown(client, connection.getChannel(),
                    (ExecutorService) field(RpcClient.class, "clientEventExecutor", client),
                    (ExecutorService) field(GrpcClient.class, "grpcExecutor", client));
        }

        void assertShutdown() {
            assertThat(client.isShutdown()).as("original RPC must be shut down").isTrue();
            assertThat(client.isRunning()).isFalse();
            assertThat(channel.isShutdown()).as("original channel must be shut down").isTrue();
            assertThat(events.isShutdown()).as("RPC event executor must be shut down").isTrue();
            assertThat(grpc.isShutdown()).as("gRPC executor must be shut down").isTrue();
        }

        boolean terminated() {
            return channel.isTerminated() && events.isTerminated() && grpc.isTerminated();
        }
    }

    record ConfigShutdown(String prefix, List<RpcShutdown> rpcs, ExecutorService scheduler,
            List<ThreadPoolExecutor> listenerPools) {
        static ConfigShutdown capture(ConfigService config) throws ReflectiveOperationException {
            ClientWorker worker = configWorker(config);
            String prefix = field(ClientWorker.class, "uuid", worker) + "_config-";
            List<RpcShutdown> rpcs = new java.util.ArrayList<>();
            for (var entry : RpcClientFactory.getAllClientEntries()) {
                if (entry.getKey().startsWith(prefix)) {
                    rpcs.add(RpcShutdown.capture(entry.getValue()));
                }
            }
            assertThat(rpcs).as("capture original transports, never create replacements").isNotEmpty();
            Object agent = worker.getAgent();
            ExecutorService scheduler = (ExecutorService) field(ConfigTransportClient.class, "executor", agent);
            Map<?, ?> listeners = (Map<?, ?>) field(agent.getClass(), "multiTaskExecutor", agent);
            return new ConfigShutdown(prefix, List.copyOf(rpcs), scheduler,
                    listeners.values().stream().map(ThreadPoolExecutor.class::cast).toList());
        }

        long retainedListenerPools() {
            // Observe the original pools; never terminate them here to hide a regression.
            return listenerPools.stream().filter(pool -> !pool.isShutdown()).count();
        }

        void assertClosed(Duration timeout) throws Exception {
            assertThat(scheduler.isShutdown()).as("config login/listen scheduler must be shut down").isTrue();
            for (RpcShutdown rpc : rpcs) {
                rpc.assertShutdown();
            }
            assertFactoryAbsent();
            assertThat(listenerPools.stream().allMatch(ExecutorService::isShutdown))
                    .as("captured listener pools must be shut down by SDK 2.5.3").isTrue();
            // Bounded condition wait for actual termination, not a sleep or a
            // post-close SDK health call. Keep the fixture's existing 60s bound.
            await("captured config resources terminate", () -> scheduler.isTerminated()
                    && rpcs.stream().allMatch(RpcShutdown::terminated)
                    && listenerPools.stream().allMatch(pool -> pool.isTerminated()
                            && pool.getActiveCount() == 0 && pool.getQueue().isEmpty()),
                    timeout);
            assertFactoryAbsent();
        }

        private void assertFactoryAbsent() {
            assertThat(RpcClientFactory.getAllClientEntries().stream()
                    .noneMatch(entry -> entry.getKey().startsWith(prefix)))
                    .as("no original or replacement config transport may remain registered").isTrue();
        }
    }

    private static Response requestOnConfigTransport(ConfigService config, Request request) throws Exception {
        // Pinned 2.5.3 test-only access: ConfigService hides unsuccessful async
        // batch responses. Reuse its existing task-0 RPC and security headers;
        // never open a raw unauthenticated channel or alter SDK internals.
        Object agent = configWorker(config).getAgent();
        Method ensureClient = agent.getClass().getDeclaredMethod("ensureRpcClient", String.class);
        RpcClient client = (RpcClient) invoke(ensureClient, agent, "0");
        Method requestProxy = agent.getClass().getDeclaredMethod("requestProxy", RpcClient.class, Request.class, long.class);
        return (Response) invoke(requestProxy, agent, client, request, 5000L);
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        method.setAccessible(true);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) { throw exception; }
            if (failure.getCause() instanceof Error error) { throw error; }
            throw new IllegalStateException("Unexpected reflected failure type");
        }
    }

    private static void awaitConfigEvent(ConfigService config, BlockingQueue<String> events,
            String expected, String label) throws Exception {
        verifyCallback(() -> awaitEvent(events, expected::equals, label),
                () -> expected.equals(configWorker(config).getAgent().queryConfig(FIXTURE_DATA_ID,
                        FIXTURE_CONFIG_GROUP, required("NACOS_FIXTURE_CONFIG_NAMESPACE"), 5000, false).getContent()));
    }

    static void verifyCallback(CheckedAction callback, CheckedCondition readback) throws Exception {
        callbackReadback = "not-run";
        try {
            callback.run();
        } catch (AssertionError failure) {
            // Diagnostic only, after failure. A successful direct query can
            // never replace the required SDK callback or change the outcome.
            try {
                callbackReadback = readback.matches() ? "matches" : "differs";
            } catch (Exception | AssertionError probeFailure) {
                callbackReadback = "request-failed";
            }
            throw failure;
        }
    }

    static String failureDiagnostic(Throwable failure) {
        // Never export SDK exception messages or request values. Those can
        // contain credentials; numeric codes and these fixed labels are safe.
        StringBuilder diagnostic = new StringBuilder("phase=" + phase + "\n");
        diagnostic.append("callback-server-readback=").append(callbackReadback).append('\n');
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

    private static Properties configProperties(boolean authenticated, boolean wrongPassword) {
        Properties properties = properties(false, false);
        properties.setProperty(PropertyKeyConst.NAMESPACE, required("NACOS_FIXTURE_CONFIG_NAMESPACE"));
        if (authenticated) {
            properties.setProperty(PropertyKeyConst.USERNAME, required("NACOS_FIXTURE_CONFIG_USERNAME"));
            properties.setProperty(PropertyKeyConst.PASSWORD,
                    required(wrongPassword ? "NACOS_FIXTURE_WRONG_PASSWORD" : "NACOS_FIXTURE_CONFIG_PASSWORD"));
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

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
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
