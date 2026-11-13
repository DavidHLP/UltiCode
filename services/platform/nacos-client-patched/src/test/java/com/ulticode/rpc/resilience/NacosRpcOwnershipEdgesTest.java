package com.ulticode.rpc.resilience;

import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcSdkClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@org.junit.jupiter.api.parallel.ResourceLock("nacos-rpc-fixture")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class NacosRpcOwnershipEdgesTest {
    static class Client extends GrpcSdkClient {
        final CountDownLatch creators = new CountDownLatch(2);
        final boolean gate;
        Client(boolean gate) { super(NacosActualRpcLifecycleTest.config("extra-"+UUID.randomUUID(),2000)); this.gate=gate; }
        @Override protected ThreadPoolExecutor createGrpcExecutor(String ip) {
            ThreadPoolExecutor e=super.createGrpcExecutor(ip);
            if (gate) {
                creators.countDown();
                try { NacosActualRpcLifecycleTest.await(creators,"competing executor creators"); }
                catch(Exception x) { e.shutdownNow();throw new RuntimeException(x); }
            }
            return e;
        }
        void reconnectNow() { reconnect(null,false); }
    }
    static void concurrent() throws Exception {
        try (NacosActualRpcLifecycleTest.LocalProtocolServer server=new NacosActualRpcLifecycleTest.LocalProtocolServer(true,true)) {
            Client c=new Client(true);
            AtomicReference<Connection> a=new AtomicReference<>(), b=new AtomicReference<>();
            NacosActualRpcLifecycleTest.Async first=new NacosActualRpcLifecycleTest.Async("creator-a",()->a.set(c.connectToServer(new RpcClient.ServerInfo("127.0.0.1",server.port()))));
            NacosActualRpcLifecycleTest.Async second=new NacosActualRpcLifecycleTest.Async("creator-b",()->b.set(c.connectToServer(new RpcClient.ServerInfo("127.0.0.1",server.port()))));
            try {
                first.join();second.join();
                NacosActualRpcLifecycleTest.require(a.get()!=null && b.get()!=null,"Concurrent negotiation failed");
                NacosActualRpcLifecycleTest.require(a.get().isAbilitiesSet() && b.get().isAbilitiesSet(),"Crossed ability negotiation");
                NacosActualRpcLifecycleTest.require(((Set<?>)NacosActualRpcLifecycleTest.field(c,"ownedExecutors")).size()==1,"Losing executor retained");
                c.shutdown();NacosActualRpcLifecycleTest.assertClosed(c,server);
            } finally { c.shutdown(); }
        }
    }
    static void retire() throws Exception {
        try (NacosActualRpcLifecycleTest.LocalProtocolServer server=new NacosActualRpcLifecycleTest.LocalProtocolServer(true,true)) {
            Client c=new Client(false);c.serverListFactory(new NacosActualRpcLifecycleTest.GateServers(server.port()));
            try {
                c.start();
                for(int i=0;i<20;i++) {
                    c.reconnectNow();
                    NacosActualRpcLifecycleTest.require(c.isRunning(),"Reconnect failed");
                    int retained=((Map<?,?>)NacosActualRpcLifecycleTest.field(c,"ownedChannels")).size();
                    NacosActualRpcLifecycleTest.require(retained<=2,"Unbounded retired channels: "+retained);
                }
                c.shutdown();NacosActualRpcLifecycleTest.assertClosed(c,server);
                NacosActualRpcLifecycleTest.require(((Map<?,?>)NacosActualRpcLifecycleTest.field(c,"ownedChannels")).isEmpty(),"Closed ownership retained");
            } finally { c.shutdown(); }
        }
    }
    static void reentrantClose() throws Exception {
        try (NacosActualRpcLifecycleTest.LocalProtocolServer server=new NacosActualRpcLifecycleTest.LocalProtocolServer(true,true)) {
            GrpcSdkClient c=new GrpcSdkClient(NacosActualRpcLifecycleTest.config("reentrant-"+UUID.randomUUID(),2000)) {
                @Override protected String getClientVersion() {
                    try { shutdown(); } catch(Exception e) { throw new RuntimeException(e); }
                    return super.getClientVersion();
                }
            };
            try {
                Connection result=c.connectToServer(new RpcClient.ServerInfo("127.0.0.1",server.port()));
                NacosActualRpcLifecycleTest.require(result==null,"Reentrant close published connection");
                NacosActualRpcLifecycleTest.assertClosed(c,server);
            } finally { c.shutdown(); }
        }
    }
    private String previousPortOffset;
    @org.junit.jupiter.api.BeforeEach
    void prepareIsolatedProtocol() {
        NacosActualRpcLifecycleTest.require("true".equals(System.getProperty("com.google.protobuf.error_on_unsafe_pre22_gencode")), "Strict protobuf guard required");
        NacosActualRpcLifecycleTest.require(System.getProperty("com.google.protobuf.use_unsafe_pre22_gencode") == null, "Unsafe gencode override must be absent, even false disables checks");
        previousPortOffset = System.getProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY);
        System.setProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY, "0");
    }
    @org.junit.jupiter.api.AfterEach
    void restoreProtocolProperty() {
        if (previousPortOffset == null) System.clearProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY);
        else System.setProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY, previousPortOffset);
    }

    @org.junit.jupiter.api.Test
    void concurrentCreatorsAndNegotiation() throws Exception { concurrent(); }
    @org.junit.jupiter.api.Test
    void twentyReconnectsRetireOwnedChannels() throws Exception { retire(); }
    @org.junit.jupiter.api.Test
    void setupCallbackCanCloseReentrantly() throws Exception { reentrantClose(); }
}
