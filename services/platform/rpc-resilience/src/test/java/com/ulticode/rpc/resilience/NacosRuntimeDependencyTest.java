package com.ulticode.rpc.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NacosRuntimeDependencyTest {

    @Test
    void excludesTheSdkWithUnmanagedShadedDependencies() throws Exception {
        assertThat(NacosNamingService.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith("-pure.jar");
        assertThat(getClass().getClassLoader().getResources(
                "com/alibaba/nacos/api/NacosFactory.class").asIterator())
                .toIterable().hasSize(1);
        assertThat(Payload.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .contains("nacos-api-compat");
        assertThat(Boolean.getBoolean("com.google.protobuf.error_on_unsafe_pre22_gencode")).isTrue();
        assertThat(getClass().getClassLoader().getResource(
                "com/alibaba/nacos/shaded/com/google/common/collect/ImmutableList.class"))
                .isNull();
    }

    @Test
    void roundTripsNacosMessagesWithTheManagedProtobufRuntime() throws Exception {
        Payload payload = payload();
        assertThat(Payload.parseFrom(payload.toByteArray())).isEqualTo(payload);
    }

    @Test
    void exchangesNacosMessagesOverTheManagedGrpcTransport() throws Exception {
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new RequestGrpc.RequestImplBase() {
                    @Override
                    public void request(Payload request, StreamObserver<Payload> response) {
                        response.onNext(request);
                        response.onCompleted();
                    }
                }).build().start();
        ManagedChannel channel = null;
        try {
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext().build();
            assertThat(RequestGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                    .request(payload())).isEqualTo(payload());
        } finally {
            if (channel != null) {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Payload payload() {
        return Payload.newBuilder()
                .setMetadata(Metadata.newBuilder().setType("ServerCheckRequest")
                        .putHeaders("requestId", "dependency-regression"))
                .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8("{}")))
                .build();
    }
}
