package com.ulticode.rpc.resilience;

import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.request.SetupAckRequest;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.grpc.DefaultGrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import com.alibaba.nacos.common.remote.client.grpc.GrpcSdkClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import io.grpc.Attributes;
import io.grpc.Server;
import io.grpc.ServerTransportFilter;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Independent protocol fixture; actual SDK factory, constructor, start, connect, reconnect and shutdown. */
@org.junit.jupiter.api.parallel.ResourceLock("nacos-rpc-fixture")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
final class NacosActualRpcLifecycleTest {
    static final long WAIT_MS = 5000;
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void await(CountDownLatch latch, String what) throws Exception {
        require(latch.await(WAIT_MS, TimeUnit.MILLISECONDS), "Timed out: " + what);
    }
    static boolean eventually(BooleanSupplier condition, long ms) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        return condition.getAsBoolean();
    }
    static Object field(Object o, String name) throws Exception {
        for (Class<?> c=o.getClass(); c!=null; c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    interface Checked { void run() throws Exception; }
    static final class Async {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread thread;
        Async(String name, Checked task) {
            thread = new Thread(() -> { try { task.run(); } catch (Throwable e) { error.set(e); } }, name);
            thread.setDaemon(true); thread.start();
        }
        void join() throws Exception {
            thread.join(WAIT_MS);
            require(!thread.isAlive(), "Thread did not complete: " + thread.getName());
            if (error.get()!=null) throw new AssertionError("Async action failed", error.get());
        }
    }
    /** Injectable SDK ServerListFactory. Gate intentionally consumes interruption: shutdownNow is advisory. */
    static final class GateServers implements ServerListFactory {
        final String address;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;
        final AtomicInteger consumedInterrupts = new AtomicInteger();
        GateServers(int port) { address="127.0.0.1:"+port; }
        void arm() { entered=new CountDownLatch(1); release=new CountDownLatch(1); }
        void unblock() { if (release!=null) release.countDown(); }
        public String genNextServer() {
            CountDownLatch gate=release;
            if (gate!=null) {
                entered.countDown();
                long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
                for (;;) {
                    try {
                        if (!gate.await(Math.max(1, end-System.nanoTime()), TimeUnit.NANOSECONDS))
                            throw new AssertionError("Server-list gate timed out");
                        break;
                    } catch (InterruptedException e) { consumedInterrupts.incrementAndGet(); }
                }
            }
            return address;
        }
        public String getCurrentServer() { return address; }
        public List<String> getServerList() { return Collections.singletonList(address); }
    }
    static final class LocalProtocolServer implements AutoCloseable {
        final AtomicInteger activeTransports=new AtomicInteger();
        final AtomicInteger streams=new AtomicInteger();
        final AtomicInteger checks=new AtomicInteger();
        final CountDownLatch setup=new CountDownLatch(1);
        final Server server;
        LocalProtocolServer(boolean advertiseAbility, boolean acknowledgeAbility) throws Exception {
            server=NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1",0))
                .addTransportFilter(new ServerTransportFilter() {
                    public Attributes transportReady(Attributes a) { activeTransports.incrementAndGet(); return a; }
                    public void transportTerminated(Attributes a) { activeTransports.decrementAndGet(); }
                })
                .addService(new RequestGrpc.RequestImplBase() {
                    public void request(Payload p, StreamObserver<Payload> output) {
                        Object request=GrpcUtils.parse(p);
                        if (request instanceof ServerCheckRequest) {
                            int n=checks.incrementAndGet();
                            output.onNext(GrpcUtils.convert(new ServerCheckResponse("synthetic-"+n,advertiseAbility)));
                        } else if (request instanceof HealthCheckRequest) {
                            output.onNext(GrpcUtils.convert(new HealthCheckResponse()));
                        } else throw new AssertionError("Unexpected unary request: "+request.getClass());
                        output.onCompleted();
                    }
                })
                .addService(new BiRequestStreamGrpc.BiRequestStreamImplBase() {
                    public StreamObserver<Payload> requestBiStream(StreamObserver<Payload> output) {
                        streams.incrementAndGet();
                        return new StreamObserver<Payload>() {
                            boolean ended;
                            void end() { if (!ended) { ended=true; streams.decrementAndGet(); } }
                            public void onNext(Payload p) {
                                Object request=GrpcUtils.parse(p);
                                if (request instanceof ConnectionSetupRequest) {
                                    setup.countDown();
                                    if (acknowledgeAbility) output.onNext(GrpcUtils.convert(new SetupAckRequest(Collections.emptyMap())));
                                }
                            }
                            public void onError(Throwable e) { end(); }
                            public void onCompleted() { end(); output.onCompleted(); }
                        };
                    }
                }).build().start();
        }
        int port() { return server.getPort(); }
        public void close() throws Exception { server.shutdownNow(); require(server.awaitTermination(5,TimeUnit.SECONDS),"Fixture server did not terminate"); }
    }
    static GrpcClientConfig config(String name, long capabilityMs) {
        return DefaultGrpcClientConfig.newBuilder().setName(name).setRetryTimes(0)
            .setTimeOutMills(1000).setConnectionKeepAlive(60000).setServerCheckTimeOut(2000L)
            .setThreadPoolCoreSize(1).setThreadPoolMaxSize(2).setCapabilityNegotiationTimeout(capabilityMs).build();
    }
    static RpcClient client(String name, GateServers servers, long capabilityMs) {
        RpcClient client=RpcClientFactory.createClient(name,ConnectionType.GRPC,config(name,capabilityMs));
        require(client.getClass()==GrpcSdkClient.class,"Expected actual GrpcSdkClient, got "+client.getClass());
        client.serverListFactory(servers); return client;
    }
    static String state(RpcClient c, LocalProtocolServer s) throws Exception {
        ExecutorService event=(ExecutorService)field(c,"clientEventExecutor");
        ExecutorService grpc=(ExecutorService)field(c,"grpcExecutor");
        Connection connection=(Connection)field(c,"currentConnection");
        return "status="+field(c,"rpcClientStatus")+", event="+(event==null?"null":event.isShutdown()+"/"+event.isTerminated())+
            ", grpc="+(grpc==null?"null":grpc.isShutdown()+"/"+grpc.isTerminated())+
            ", current="+(connection==null?"null":connection.getConnectionId())+
            ", transports="+s.activeTransports+", streams="+s.streams+", checks="+s.checks;
    }
    static void assertClosed(RpcClient c, LocalProtocolServer s) throws Exception {
        ExecutorService event=(ExecutorService)field(c,"clientEventExecutor");
        ExecutorService grpc=(ExecutorService)field(c,"grpcExecutor");
        if (event!=null) event.awaitTermination(2,TimeUnit.SECONDS);
        if (grpc!=null) grpc.awaitTermination(2,TimeUnit.SECONDS);
        eventually(()->s.activeTransports.get()==0 && s.streams.get()==0,1500);
        String observed=state(c,s); System.out.println("OBSERVED "+observed);
        require(c.isShutdown(),"Terminal SHUTDOWN lost: "+observed);
        require(event==null || event.isTerminated(),"Event executor leaked: "+observed);
        require(grpc==null || grpc.isTerminated(),"gRPC executor leaked: "+observed);
        require(s.activeTransports.get()==0 && s.streams.get()==0,"Transport/stream leaked: "+observed);
    }
    static void normal(boolean abilities) throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(abilities,abilities)) {
            String name="normal-"+UUID.randomUUID(); GateServers servers=new GateServers(s.port());
            RpcClient c=client(name,servers,1000);
            try {
                require(RpcClientFactory.createClient(name,ConnectionType.GRPC,config(name,1000))==c,"Factory duplicated ID");
                c.start(); require(c.isRunning(),"Normal actual SDK start failed"); await(s.setup,"normal setup");
                require(c.request(new HealthCheckRequest(),1000).isSuccess(),"Actual unary roundtrip failed");
                RpcClientFactory.destroyClient(name);
                require(RpcClientFactory.getClient(name)==null,"Factory retained destroyed client");
                c.start(); // Shutdown is terminal, even on explicit repeated start.
                assertClosed(c,s);
            } finally { c.shutdown(); RpcClientFactory.destroyClient(name); }
        }
    }
    static void startupShutdown() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(false,false)) {
            String name="startup-"+UUID.randomUUID(); GateServers servers=new GateServers(s.port()); servers.arm();
            RpcClient c=client(name,servers,1000); Async start=new Async("actual-start",c::start);
            try {
                await(servers.entered,"startup entered actual server-list callback");
                c.shutdown(); require(c.isShutdown(),"Shutdown did not initially take effect");
                servers.unblock(); start.join(); assertClosed(c,s);
            } finally { servers.unblock(); start.thread.join(WAIT_MS); c.shutdown(); RpcClientFactory.destroyClient(name); }
        }
    }
    static void reconnectShutdown() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(false,false)) {
            String name="reconnect-"+UUID.randomUUID(); GateServers servers=new GateServers(s.port());
            RpcClient c=client(name,servers,1000);
            try {
                c.start(); require(c.isRunning(),"Initial connection failed");
                servers.arm(); c.switchServerAsync(); await(servers.entered,"reconnect server-list callback");
                c.shutdown(); servers.unblock();
                ExecutorService event=(ExecutorService)field(c,"clientEventExecutor"); event.awaitTermination(4,TimeUnit.SECONDS);
                System.out.println("INTERRUPTS_CONSUMED "+servers.consumedInterrupts);
                assertClosed(c,s);
            } finally { servers.unblock(); c.shutdown(); RpcClientFactory.destroyClient(name); }
        }
    }
    /** Only wraps the real connect result to expose the pre-publication window; no fake connection. */
    static final class GatedRealGrpcSdkClient extends GrpcSdkClient {
        volatile boolean armed;
        final CountDownLatch connected=new CountDownLatch(1), release=new CountDownLatch(1);
        volatile Connection provisional;
        GatedRealGrpcSdkClient(String name) { super(config(name,1000)); }
        @Override public Connection connectToServer(RpcClient.ServerInfo info) {
            Connection actual=super.connectToServer(info);
            if (armed && actual!=null) {
                provisional=actual; connected.countDown();
                long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
                for (;;) {
                    try {
                        require(release.await(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS),"Publication gate timeout");
                        break;
                    } catch (InterruptedException ignored) { /* shutdownNow cannot guarantee external hook aborts */ }
                }
            }
            return actual;
        }
    }
    static void reconnectPublication() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(false,false)) {
            GatedRealGrpcSdkClient c=new GatedRealGrpcSdkClient("publication-"+UUID.randomUUID());
            c.serverListFactory(new GateServers(s.port()));
            try {
                c.start(); require(c.isRunning(),"Initial real connection failed");
                c.armed=true; c.switchServerAsync(); await(c.connected,"real reconnect completed before publication");
                require(s.checks.get()==2,"Second real connection was not established");
                c.shutdown(); c.release.countDown();
                ExecutorService event=(ExecutorService)field(c,"clientEventExecutor");event.awaitTermination(4,TimeUnit.SECONDS);
                System.out.println("INSTRUMENTATION subclass wraps only result of super.connectToServer; RpcClient.reconnect/start/shutdown unchanged");
                assertClosed(c,s);
            } finally { c.release.countDown(); c.shutdown(); if(c.provisional!=null)c.provisional.close(); }
        }
    }
    static void interruptedNegotiation() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(true,false)) {
            String name="interrupted-"+UUID.randomUUID(); GateServers servers=new GateServers(s.port());
            RpcClient c=client(name,servers,10000); AtomicReference<Connection> result=new AtomicReference<>();
            Async connect=new Async("actual-connect",()->result.set(c.connectToServer(new RpcClient.ServerInfo("127.0.0.1",s.port()))));
            try {
                await(s.setup,"connection setup before interrupt"); connect.thread.interrupt(); connect.join();
                require(result.get()==null,"Interrupted negotiation unexpectedly succeeded");
                c.shutdown(); assertClosed(c,s);
            } finally { connect.thread.interrupt(); connect.thread.join(WAIT_MS); if(result.get()!=null)result.get().close();c.shutdown(); RpcClientFactory.destroyClient(name); }
        }
    }
    static void negotiationTimeout() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(true,false)) {
            String name="timeout-"+UUID.randomUUID(); GateServers servers=new GateServers(s.port());
            RpcClient c=client(name,servers,200);
            try {
                Connection result=c.connectToServer(new RpcClient.ServerInfo("127.0.0.1",s.port()));
                require(result==null,"No-ack negotiation unexpectedly succeeded");
                c.shutdown(); assertClosed(c,s);
            } finally { c.shutdown(); RpcClientFactory.destroyClient(name); }
        }
    }
    static void connectAfterShutdown() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(false,false)) {
            String name="closed-"+UUID.randomUUID(); GateServers servers=new GateServers(s.port());
            RpcClient c=client(name,servers,1000); Connection result=null;
            try {
                c.shutdown(); result=c.connectToServer(new RpcClient.ServerInfo("127.0.0.1",s.port()));
                System.out.println("OBSERVED "+state(c,s));
                require(result==null && s.checks.get()==0,"Closed client allocated a fresh actual channel/connection");
                assertClosed(c,s);
            } finally { if(result!=null)result.close();c.shutdown(); RpcClientFactory.destroyClient(name); }
        }
    }
    static boolean contains(Throwable root, Throwable target) {
        return contains(root,target,java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>()));
    }
    static boolean contains(Throwable root,Throwable target,java.util.Set<Throwable> seen) {
        if(root==null||!seen.add(root))return false;
        if(root==target||contains(root.getCause(),target,seen))return true;
        for(Throwable child:root.getSuppressed())if(contains(child,target,seen))return true;
        return false;
    }
    static void reconnectRetirement() throws Exception {
        try(LocalProtocolServer s=new LocalProtocolServer(false,false)) {
            String name="retirement-"+UUID.randomUUID();RpcClient c=client(name,new GateServers(s.port()),1000);
            try {
                c.start();require(c.isRunning(),"Initial actual connection failed");
                for(int i=0;i<5;i++) {
                    Connection previous=(Connection)field(c,"currentConnection");
                    c.switchServerAsync();
                    require(eventually(()->{try{return c.isRunning()&&field(c,"currentConnection")!=previous;}catch(Exception e){throw new RuntimeException(e);}},3000),"Reconnect did not publish");
                    require(((GrpcConnection)previous).getChannel().isShutdown(),"Retired real channel not shut down");
                }
                Object retained=null;try{retained=field(c,"ownedChannels");}catch(NoSuchFieldException noOwnershipMap){}
                System.out.println("OBSERVED successfulReconnects=5 retainedChannelRecords="+(retained==null?"no-added-map":((java.util.Map<?,?>)retained).size()));
                require(retained==null||((java.util.Map<?,?>)retained).size()<=2,"Closed reconnect channels retained without bound");
                c.shutdown();assertClosed(c,s);
            } finally {c.shutdown();RpcClientFactory.destroyClient(name);}
        }
    }
    static void shutdownFailureRetry() throws Exception {
        try (LocalProtocolServer s=new LocalProtocolServer(false,false)) {
            String name="failure-retry-"+UUID.randomUUID();
            RpcClient c=client(name,new GateServers(s.port()),1000);
            c.start();require(c.isRunning(),"Initial actual connection failed");
            java.util.concurrent.ScheduledExecutorService actual=(java.util.concurrent.ScheduledExecutorService)field(c,"clientEventExecutor");
            java.util.concurrent.atomic.AtomicBoolean first=new java.util.concurrent.atomic.AtomicBoolean(true);
            RuntimeException sentinel=new SecurityException("synthetic-one-time-shutdown-failure");
            Object wrapper=java.lang.reflect.Proxy.newProxyInstance(NacosActualRpcLifecycleTest.class.getClassLoader(),
                new Class<?>[]{java.util.concurrent.ScheduledExecutorService.class},(proxy,method,args)->{
                    if(method.getName().equals("shutdownNow")&&first.getAndSet(false))throw sentinel;
                    try{return method.invoke(actual,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            Field f=RpcClient.class.getDeclaredField("clientEventExecutor");f.setAccessible(true);f.set(c,wrapper);
            try {
                Throwable failed=null;try{c.shutdown();}catch(Throwable t){failed=t;}
                eventually(()->s.activeTransports.get()==0 && s.streams.get()==0,1500);
                ExecutorService grpc=(ExecutorService)field(c,"grpcExecutor");
                System.out.println("INSTRUMENTATION one-time failure wrapper around actual event executor shutdownNow; all RPC/channel operations real");
                System.out.println("OBSERVED firstFailure="+failed+", "+state(c,s));
                boolean peerClean=c.isShutdown()&&grpc.isShutdown()&&s.activeTransports.get()==0;
                c.shutdown();assertClosed(c,s);
                require(contains(failed,sentinel),"Cleanup failure identity not preserved in cause/suppressed tree");
                require(peerClean,"Event executor cleanup failure skipped independent channel/grpc cleanup");
                require(actual.isTerminated(),"Repeated shutdown lost failed event-executor retry ownership");
            } finally { actual.shutdownNow();c.shutdown();RpcClientFactory.destroyClient(name); }
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
    void normalLegacy() throws Exception { normal(false); }
    @org.junit.jupiter.api.Test
    void normalAbilities() throws Exception { normal(true); }
    @org.junit.jupiter.api.Test
    void startupShutdownContract() throws Exception { startupShutdown(); }
    @org.junit.jupiter.api.Test
    void reconnectShutdownContract() throws Exception { reconnectShutdown(); }
    @org.junit.jupiter.api.Test
    void lateReconnectPublication() throws Exception { reconnectPublication(); }
    @org.junit.jupiter.api.Test
    void interruptedNegotiationContract() throws Exception { interruptedNegotiation(); }
    @org.junit.jupiter.api.Test
    void negotiationTimeoutContract() throws Exception { negotiationTimeout(); }
    @org.junit.jupiter.api.Test
    void connectAfterShutdownContract() throws Exception { connectAfterShutdown(); }
    @org.junit.jupiter.api.Test
    void shutdownFailureRetryContract() throws Exception { shutdownFailureRetry(); }
    @org.junit.jupiter.api.Test
    void reconnectRetirementContract() throws Exception { reconnectRetirement(); }
}
