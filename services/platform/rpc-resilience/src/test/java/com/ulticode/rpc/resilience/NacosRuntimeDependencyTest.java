package com.ulticode.rpc.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSet;
import com.sun.management.ThreadMXBean;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NacosRuntimeDependencyTest {

    private static volatile Payload allocationSink;

    @BeforeAll
    static void requiresSafeGencodeWithoutAnUnsafeOverride() {
        assertThat(System.getProperty("com.google.protobuf.error_on_unsafe_pre22_gencode"))
                .as("the forked test JVM must reject legacy vulnerable generated code").isEqualTo("true");
        assertThat(Boolean.getBoolean("com.google.protobuf.use_unsafe_pre22_gencode")).isFalse();
    }

    @Test
    void excludesTheSdkWithUnmanagedShadedDependencies() throws Exception {
        assertPinnedNacosRuntime();
    }

    static void assertPinnedNacosRuntime() throws Exception {
        assertThat(NacosNamingService.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith("-pure.jar");
        // The API gencode patch must not replace SDK lifecycle implementation.
        for (Class<?> clientClass : List.of(NacosConfigService.class, ClientWorker.class)) {
            assertThat(clientClass.getProtectionDomain().getCodeSource().getLocation())
                    .isEqualTo(NacosNamingService.class.getProtectionDomain().getCodeSource().getLocation());
            assertThat(clientClass.getProtectionDomain().getCodeSource().getLocation().toString())
                    .endsWith("nacos-client-2.5.3-pure.jar");
        }
        assertThat(ConnectionType.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .as("common and client must use the patched API's upstream baseline")
                .endsWith("nacos-common-2.5.3.jar");
        assertSinglePatchedApi();
        assertThat(NacosRuntimeDependencyTest.class.getClassLoader().getResource(
                "com/alibaba/nacos/shaded/com/google/common/collect/ImmutableList.class")).isNull();
    }

    @Test
    void initializesNamingSerializationAgainstThePatchedApi() {
        // Ordinary naming startup links API selector/serializer classes here.
        // A protocol-only round trip does not exercise this SDK/API boundary.
        InitUtils.initSerialization();
    }

    @Test
    void retainsThe253ClientMetricsApiConstant() throws Exception {
        // Reflection avoids a compile-time inlined constant masking an older API JAR.
        assertThat(PropertyKeyConst.class.getField("ENABLE_CLIENT_METRICS").get(null))
                .isEqualTo("enableClientMetrics");
    }

    static void assertSinglePatchedApi() throws Exception {
        ClassLoader loader = NacosRuntimeDependencyTest.class.getClassLoader();
        for (Class<?> apiClass : List.of(NacosFactory.class, PropertyKeyConst.class, Payload.class, Metadata.class,
                RequestGrpc.class, BiRequestStreamGrpc.class)) {
            assertThat(loader.getResources(apiClass.getName().replace('.', '/') + ".class").asIterator())
                    .toIterable().hasSize(1);
            assertThat(apiClass.getProtectionDomain().getCodeSource().getLocation())
                    .isEqualTo(NacosFactory.class.getProtectionDomain().getCodeSource().getLocation());
        }
        assertThat(loader.getResource("META-INF/maven/com.alibaba.nacos/nacos-api/pom.properties")).isNull();
        String source = NacosFactory.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
        assertThat(source.endsWith("nacos-api-patched-2.5.3-ulticode-1.jar")
                || source.endsWith("/nacos-api-patched/target/classes/"))
                .as("API classes must originate from the owned artifact: %s", source).isTrue();
        String provenance = "META-INF/nacos-patch/provenance.json";
        assertThat(loader.getResources(provenance).asIterator()).toIterable().hasSize(1);
        String resourceRoot = source.endsWith(".jar") ? "jar:" + source + "!/" : source;
        assertThat(loader.getResource(provenance).toExternalForm()).isEqualTo(resourceRoot + provenance);
        try (InputStream input = loader.getResourceAsStream(provenance)) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("\"artifact\": \"pkg:maven/com.ulticode/nacos-api-patched@2.5.3-ulticode-1\"",
                            "\"coordinates\": \"com.alibaba.nacos:nacos-api:2.5.3\"",
                            "\"sha256\": \"8a0485b4c9ec3df44555c947163421df52824faae17906112651873076fdcee0\"");
        }
        // Maven test uses reactor target/classes; Maven adds its own metadata
        // only when packaging. The standalone registry gate requires the JAR.
        if (source.endsWith(".jar")) {
            String marker = "META-INF/maven/com.ulticode/nacos-api-patched/pom.properties";
            assertThat(loader.getResources(marker).asIterator()).toIterable().hasSize(1);
            Properties identity = new Properties();
            try (InputStream input = loader.getResourceAsStream(marker)) {
                identity.load(input);
            }
            assertThat(identity.getProperty("groupId")).isEqualTo("com.ulticode");
            assertThat(identity.getProperty("artifactId")).isEqualTo("nacos-api-patched");
            assertThat(identity.getProperty("version")).isEqualTo("2.5.3-ulticode-1");
        }
    }

    @Test
    void fixtureWaitsForEveryTransportBeforeSendingBusinessRequests() throws Exception {
        AtomicInteger consumerPolls = new AtomicInteger();
        AtomicInteger configPolls = new AtomicInteger();
        NacosRegistryIT.awaitConnected(() -> "UP",
                () -> consumerPolls.incrementAndGet() == 1 ? "DOWN" : "UP",
                () -> { configPolls.incrementAndGet(); return "UP"; });
        assertThat(consumerPolls.get()).isEqualTo(2);
        assertThat(configPolls.get()).isEqualTo(1);
    }

    @Test
    void fixtureReadinessFailsClosedWithoutSwallowingRequestErrors() {
        assertThatThrownBy(() -> NacosRegistryIT.await("disconnected fixture", () -> false, Duration.ZERO))
                .isInstanceOf(AssertionError.class).hasMessageContaining("disconnected fixture");
        NacosException forbidden = new NacosException(NacosException.NO_RIGHT, "must not be retried");
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> NacosRegistryIT.await("forbidden", () -> {
            attempts.incrementAndGet();
            throw forbidden;
        }, Duration.ofSeconds(60))).isSameAs(forbidden);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void fixtureDiagnosticsDistinguishTransportAndAuthWithoutExposingMessages() {
        for (Map.Entry<Integer, String> error : Map.of(
                NacosException.CLIENT_DISCONNECT, "client-disconnected",
                NacosException.NO_RIGHT, "authorization-rejected",
                NacosException.INVALID_SERVER_STATUS, "server-not-ready",
                NacosException.NOT_FOUND, "resource-not-found",
                NacosException.SERVER_ERROR, "nacos-request-failed").entrySet()) {
            String privateValue = java.util.UUID.randomUUID().toString();
            String diagnostic = NacosRegistryIT.failureDiagnostic(new IllegalStateException(privateValue,
                    new NacosException(error.getKey(), privateValue)));
            assertThat(diagnostic).contains("nacos-error-code=" + error.getKey() + "\n",
                    "classification=" + error.getValue() + "\n").doesNotContain(privateValue);
        }
    }

    @Test
    void fixtureBatchNegativeControlRequiresAnActual403Response() throws Exception {
        var request = NacosRegistryIT.workloadBatchRequest("protobuf-fixture");
        assertThat(request.isListen()).isTrue();
        assertThat(request.getConfigListenContexts()).singleElement().satisfies(context -> {
            assertThat(context.getTenant()).isEqualTo("protobuf-fixture");
            assertThat(context.getGroup()).isEqualTo("mapping");
            assertThat(context.getDataId()).isEqualTo("com.ulticode.auth.ProtobufFixture");
            assertThat(context.getMd5()).matches("[a-f0-9]{32}");
        });
        assertThatThrownBy(() -> NacosRegistryIT.workloadBatchRequest("another-namespace"))
                .isInstanceOf(AssertionError.class);
        String privateValue = java.util.UUID.randomUUID().toString();
        ConfigChangeBatchListenResponse denied = new ConfigChangeBatchListenResponse();
        denied.setErrorInfo(NacosException.NO_RIGHT, privateValue);
        NacosRegistryIT.requireNoRightResponse(denied);
        assertThat(NacosRegistryIT.batchResponseDiagnostic(denied))
                .isEqualTo("workload-batch-result-code=500\nworkload-batch-error-code=403\n")
                .doesNotContain(privateValue);
        for (int error : List.of(NacosException.CLIENT_DISCONNECT, NacosException.SERVER_ERROR)) {
            ConfigChangeBatchListenResponse otherFailure = new ConfigChangeBatchListenResponse();
            otherFailure.setErrorInfo(error, privateValue);
            assertThatThrownBy(() -> NacosRegistryIT.requireNoRightResponse(otherFailure))
                    .isInstanceOf(AssertionError.class);
        }
        assertThatThrownBy(() -> NacosRegistryIT.requireNoRightResponse(new ConfigChangeBatchListenResponse()))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> NacosRegistryIT.requireNoRightResponse(null)).isInstanceOf(AssertionError.class);
    }

    @Test
    void fixtureProbeMatchesThePinnedClientPrivateTransportContract() throws Exception {
        assertThat(NacosConfigService.class.getDeclaredField("worker").getType()).isEqualTo(ClientWorker.class);
        Class<?> transport = Class.forName("com.alibaba.nacos.client.config.impl.ClientWorker$ConfigRpcTransportClient",
                false, getClass().getClassLoader());
        assertThat(transport.getDeclaredMethod("ensureRpcClient", String.class).getReturnType()).isEqualTo(RpcClient.class);
        assertThat(transport.getDeclaredMethod("requestProxy", RpcClient.class, Request.class, long.class).getReturnType())
                .isEqualTo(Response.class);
    }

    @Test
    void fixtureReadbackNeverReplacesAnActualCallback() throws Exception {
        AssertionError timeout = new AssertionError("callback absent");
        AtomicInteger probes = new AtomicInteger();
        NacosRegistryIT.verifyCallback(() -> {}, () -> { probes.incrementAndGet(); return true; });
        assertThat(probes.get()).isZero();
        for (boolean matches : List.of(true, false)) {
            assertThatThrownBy(() -> NacosRegistryIT.verifyCallback(() -> { throw timeout; },
                    () -> { probes.incrementAndGet(); return matches; })).isSameAs(timeout);
            assertThat(NacosRegistryIT.failureDiagnostic(timeout))
                    .contains("callback-server-readback=" + (matches ? "matches" : "differs") + "\n");
        }
        assertThat(probes.get()).isEqualTo(2);
        assertThatThrownBy(() -> NacosRegistryIT.verifyCallback(() -> { throw timeout; },
                () -> { throw new NacosException(NacosException.CLIENT_DISCONNECT, "private-request-message"); }))
                .isSameAs(timeout);
        assertThat(NacosRegistryIT.failureDiagnostic(timeout))
                .contains("callback-server-readback=request-failed\n").doesNotContain("private-request-message");
    }

    @Test
    void fixtureLifecycleChecksOriginalResourcesAndRejectsFactoryResurrection() throws Exception {
        String prefix = "fixture-lifecycle-" + java.util.UUID.randomUUID() + "-";
        RpcClient rpc = RpcClientFactory.createClient(prefix + "0", ConnectionType.GRPC, Map.of());
        // Construct, never start/connect: this is an infrastructure-free state test.
        RpcClientFactory.getAllClientEntries().removeIf(entry -> entry.getKey().equals(prefix + "0"));
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", 9).usePlaintext().build();
        ExecutorService events = Executors.newSingleThreadExecutor();
        ExecutorService grpc = Executors.newSingleThreadExecutor();
        ExecutorService scheduler = Executors.newSingleThreadExecutor();
        ExecutorService blocked = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor listener = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        var original = new NacosRegistryIT.RpcShutdown(rpc, channel, events, grpc);
        var snapshot = new NacosRegistryIT.ConfigShutdown(prefix, List.of(original), scheduler, List.of(listener));
        try {
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("scheduler");
            scheduler.shutdown();
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("original RPC");
            rpc.shutdown();
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("original channel");
            channel.shutdownNow();
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("event executor");
            events.shutdown();
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("gRPC executor");
            grpc.shutdown();
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("listener pools must be shut down");
            assertThat(snapshot.retainedListenerPools()).isEqualTo(1);
            assertThat(NacosRegistryIT.retainedListenerDiagnostic(snapshot.retainedListenerPools()))
                    .isEqualTo("config-retained-listener-pools=1\n");
            assertThat(listener.isShutdown()).isFalse(); // observation must not hide upstream retention
            listener.shutdown(); // synthetic stand-in, not an SDK pool
            snapshot.assertClosed(Duration.ofSeconds(5));
            assertThat(snapshot.retainedListenerPools()).isZero();
            assertThat(RpcClientFactory.getClient(prefix + "0")).isNull();

            RpcClientFactory.createClient(prefix + "replacement", ConnectionType.GRPC, Map.of());
            assertThatThrownBy(() -> snapshot.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("remain registered");
            RpcClientFactory.destroyClient(prefix + "replacement");

            CountDownLatch started = new CountDownLatch(1);
            blocked.submit(() -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            blocked.shutdown();
            var notTerminated = new NacosRegistryIT.ConfigShutdown(prefix, List.of(original), blocked, List.of());
            assertThatThrownBy(() -> notTerminated.assertClosed(Duration.ZERO))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("resources terminate");
        } finally {
            release.countDown();
            blocked.shutdownNow();
            // These are test-created stand-ins, never the SDK's retained pools.
            listener.shutdownNow();
            scheduler.shutdownNow();
            events.shutdownNow();
            grpc.shutdownNow();
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            rpc.shutdown();
            RpcClientFactory.destroyClient(prefix + "replacement");
        }
    }

    @Test
    void roundTripsNacosMessagesWithTheManagedProtobufRuntime() throws Exception {
        Payload payload = payload();
        assertThat(Payload.parseFrom(payload.toByteArray())).isEqualTo(payload);
        assertThat(Payload.parseFrom(new ByteArrayInputStream(payload.toByteArray()))).isEqualTo(payload);
        assertThat(payload.getMetadata().getField(Metadata.getDescriptor().findFieldByNumber(7)))
                .isInstanceOf(List.class);
        assertThat(payload.getUnknownFields().getField(99).getVarintList()).containsExactly(7L);
        for (byte[] malformed : List.of(new byte[] {(byte) 0x80}, new byte[] {0x12, 0x05, 0x00})) {
            assertThatThrownBy(() -> Payload.parseFrom(malformed))
                    .isInstanceOf(InvalidProtocolBufferException.class);
        }
    }

    @Test
    void repeatedEmbeddedMetadataMergesWithinLinearAllocationBounds() throws Exception {
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertThat(allocation.isThreadAllocatedMemorySupported()).isTrue();
        allocation.setThreadAllocatedMemoryEnabled(true);
        for (boolean unknownFields : List.of(false, true)) {
            byte[] smaller = repeatedMetadata(1024, unknownFields);
            byte[] larger = repeatedMetadata(2048, unknownFields);
            assertThat(larger.length).isLessThanOrEqualTo(32 * 1024);
            for (int warmup = 0; warmup < 10; warmup++) {
                allocationSink = Payload.parseFrom(smaller);
                allocationSink = Payload.parseFrom(larger);
            }
            long smallBytes = allocatedBytes(allocation, smaller);
            long largeBytes = allocatedBytes(allocation, larger);
            // Allocation, not elapsed time: pre-22 gencode repeatedly copies the
            // entire accumulated submessage. These generous bounds reject that
            // quadratic growth without depending on host speed or scheduling.
            assertThat(largeBytes).isLessThan(4L * 1024 * 1024);
            assertThat(largeBytes).isLessThan(3 * smallBytes + 64 * 1024);
            assertMerged(allocationSink, unknownFields);
        }
    }

    private static long allocatedBytes(ThreadMXBean allocation, byte[] wire) throws Exception {
        long minimum = Long.MAX_VALUE;
        for (int sample = 0; sample < 3; sample++) {
            long before = allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
            allocationSink = Payload.parseFrom(wire);
            minimum = Math.min(minimum,
                    allocation.getThreadAllocatedBytes(Thread.currentThread().getId()) - before);
        }
        return minimum;
    }

    @Test
    void parsesBoundedRawRegistryResponsesOverUnaryAndBidirectionalGrpc() throws Exception {
        AtomicReference<byte[]> responseWire = new AtomicReference<>(payload().toByteArray());
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(ServerServiceDefinition.builder(RequestGrpc.SERVICE_NAME)
                        .addMethod(rawResponseMethod(RequestGrpc.getRequestMethod()),
                                ServerCalls.asyncUnaryCall((Payload request, StreamObserver<byte[]> response) -> {
                                    response.onNext(responseWire.get());
                                    response.onCompleted();
                                })).build())
                .addService(ServerServiceDefinition.builder(BiRequestStreamGrpc.SERVICE_NAME)
                        .addMethod(rawResponseMethod(BiRequestStreamGrpc.getRequestBiStreamMethod()),
                                ServerCalls.asyncBidiStreamingCall((StreamObserver<byte[]> response) ->
                                        new StreamObserver<Payload>() {
                                            public void onNext(Payload request) { response.onNext(responseWire.get()); }
                                            public void onError(Throwable error) { response.onError(error); }
                                            public void onCompleted() { response.onCompleted(); }
                                        })).build())
                .build().start();
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext().build();
        try {
            assertThat(RequestGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                    .request(payload())).isEqualTo(payload());
            for (boolean unknownFields : List.of(false, true)) {
                responseWire.set(repeatedMetadata(2048, unknownFields));
                assertMerged(RequestGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                        .request(payload()), unknownFields);
                assertMerged(bidirectionalReply(channel).get(6, TimeUnit.SECONDS), unknownFields);
            }
            responseWire.set(new byte[] {0x12, 0x05, 0x00});
            assertThatThrownBy(() -> RequestGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                    .request(payload())).isInstanceOfSatisfying(StatusRuntimeException.class,
                            failure -> assertThat(failure).hasRootCauseInstanceOf(InvalidProtocolBufferException.class));
            assertThatThrownBy(() -> bidirectionalReply(channel).get(6, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(InvalidProtocolBufferException.class);
        } finally {
            assertThat(channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static CompletableFuture<Payload> bidirectionalReply(ManagedChannel channel) {
        CompletableFuture<Payload> result = new CompletableFuture<>();
        StreamObserver<Payload> request = BiRequestStreamGrpc.newStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                .requestBiStream(new StreamObserver<Payload>() {
                    public void onNext(Payload value) { result.complete(value); }
                    public void onError(Throwable error) { result.completeExceptionally(error); }
                    public void onCompleted() {}
                });
        request.onNext(payload());
        request.onCompleted();
        return result;
    }

    private static MethodDescriptor<Payload, byte[]> rawResponseMethod(MethodDescriptor<Payload, Payload> original) {
        return MethodDescriptor.<Payload, byte[]>newBuilder().setFullMethodName(original.getFullMethodName())
                .setType(original.getType()).setRequestMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance()))
                .setResponseMarshaller(new MethodDescriptor.Marshaller<>() {
                    public InputStream stream(byte[] wire) { return new ByteArrayInputStream(wire); }
                    public byte[] parse(InputStream input) { throw new UnsupportedOperationException("server writes only"); }
                }).build();
    }

    private static byte[] repeatedMetadata(int count, boolean unknownFields) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream wire = CodedOutputStream.newInstance(bytes);
        for (int index = 0; index < count; index++) {
            Metadata.Builder metadata = Metadata.newBuilder();
            if (unknownFields) {
                metadata.setUnknownFields(UnknownFieldSet.newBuilder().addField(99,
                        UnknownFieldSet.Field.newBuilder().addVarint(index).build()).build());
            } else {
                metadata.putHeaders("k" + index, "v");
            }
            wire.writeMessage(2, metadata.build());
        }
        wire.flush();
        assertThat(bytes.size()).isLessThanOrEqualTo(32 * 1024);
        return bytes.toByteArray();
    }

    private static void assertMerged(Payload response, boolean unknownFields) {
        if (unknownFields) {
            assertThat(response.getMetadata().getUnknownFields().getField(99).getVarintList())
                    .hasSize(2048).startsWith(0L).endsWith(2047L);
        } else {
            assertThat(response.getMetadata().getHeadersMap()).hasSize(2048)
                    .containsEntry("k0", "v").containsEntry("k2047", "v");
        }
    }

    private static Payload payload() {
        return Payload.newBuilder()
                .setMetadata(Metadata.newBuilder().setType("ServerCheckRequest").setClientIp("127.0.0.1")
                        .putHeaders("requestId", "dependency-regression").putHeaders("unicode", "测试"))
                .setBody(Any.newBuilder().setTypeUrl("test.invalid/Fixture").setValue(ByteString.copyFromUtf8("{}")))
                .setUnknownFields(UnknownFieldSet.newBuilder().addField(99,
                        UnknownFieldSet.Field.newBuilder().addVarint(7).build()).build())
                .build();
    }
}
