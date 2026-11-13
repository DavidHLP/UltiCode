package com.ulticode.rpc.resilience;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigServerListManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import com.alibaba.nacos.common.remote.client.grpc.GrpcSdkClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Actual worker, transport and factory construction; only the server-list callback is instrumented. */
@org.junit.jupiter.api.parallel.ResourceLock("nacos-rpc-fixture")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
final class NacosTransportAdmissionTest {
    interface Hook { void run() throws Exception; }
    static final class HookServers extends ConfigServerListManager {
        volatile Hook hook;
        final AtomicReference<Throwable> hookFailure=new AtomicReference<>();
        HookServers(NacosClientProperties properties) throws Exception { super(properties); }
        @Override public String genNextServer() {
            Hook h=hook;
            if(h!=null)try { h.run(); } catch(Throwable t) { hookFailure.compareAndSet(null,t); }
            return super.genNextServer();
        }
    }
    static RpcClient ensure(Object transport,Method ensure,String id) throws Exception {
        try{return (RpcClient)ensure.invoke(transport,id);}
        catch(InvocationTargetException e) {
            if(e.getCause() instanceof Exception)throw (Exception)e.getCause();
            throw (Error)e.getCause();
        }
    }
    static List<RpcClient> owned(String uuid) {
        List<RpcClient> result=new ArrayList<>();
        RpcClientFactory.getAllClientEntries().forEach(e->{if(e.getKey().startsWith(uuid+"_config-"))result.add(e.getValue());});return result;
    }
    static void run(String mode) throws Exception {
        try(NacosActualRpcLifecycleTest.LocalProtocolServer peer=new NacosActualRpcLifecycleTest.LocalProtocolServer(false,false)) {
            Properties p=new Properties();p.setProperty(PropertyKeyConst.SERVER_ADDR,"127.0.0.1:"+peer.port());
            p.setProperty(PropertyKeyConst.NAMESPACE,"synthetic-independent-reentry");
            p.setProperty(PropertyKeyConst.USERNAME,"");p.setProperty(PropertyKeyConst.PASSWORD,"");
            p.setProperty(PropertyKeyConst.ENABLE_CLIENT_METRICS,"false");
            NacosClientProperties properties=NacosClientProperties.PROTOTYPE.derive(p);
            HookServers servers=new HookServers(properties);servers.start();
            ClientWorker worker=new ClientWorker(new ConfigFilterChainManager(p),servers,properties);
            Object transport=NacosActualRpcLifecycleTest.field(worker,"agent");
            String uuid=(String)NacosActualRpcLifecycleTest.field(worker,"uuid");
            ExecutorService scheduler=(ExecutorService)NacosActualRpcLifecycleTest.field(transport,"executor");
            Method method=transport.getClass().getDeclaredMethod("ensureRpcClient",String.class);method.setAccessible(true);
            CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
            AtomicBoolean once=new AtomicBoolean();
            AtomicBoolean heldTransport=new AtomicBoolean(),heldWorker=new AtomicBoolean(),partialReturned=new AtomicBoolean();
            AtomicReference<Throwable> nestedFailure=new AtomicReference<>();
            AtomicReference<RpcClient> outerResult=new AtomicReference<>();
            AtomicReference<Throwable> outerFailure=new AtomicReference<>();
            List<RpcClient> captured=new ArrayList<>();
            NacosActualRpcLifecycleTest.Async creator=null;
            try {
                if(mode.equals("concurrent-same-task")) {
                    servers.hook=()->{
                        heldWorker.set(Thread.holdsLock(worker));heldTransport.set(Thread.holdsLock(transport));
                        if(once.compareAndSet(false,true)){entered.countDown();NacosActualRpcLifecycleTest.await(release,"concurrent creator release");}
                    };
                    List<NacosActualRpcLifecycleTest.Async> actors=new ArrayList<>();
                    List<RpcClient> results=java.util.Collections.synchronizedList(new ArrayList<>());
                    try {
                        for(int i=0;i<12;i++)actors.add(new NacosActualRpcLifecycleTest.Async("same-task-"+i,()->results.add(ensure(transport,method,"shared"))));
                        NacosActualRpcLifecycleTest.await(entered,"single creator entered startup");release.countDown();
                        for(NacosActualRpcLifecycleTest.Async actor:actors)actor.join();
                        captured.addAll(results);
                        java.util.Set<RpcClient> identities=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<RpcClient,Boolean>());identities.addAll(results);
                        System.out.println("OBSERVED concurrentCallers="+results.size()+" distinctSdkObjects="+identities.size()+" checks="+peer.checks+" ownedFactory="+owned(uuid).size());
                        NacosActualRpcLifecycleTest.require(results.size()==12&&identities.size()==1&&peer.checks.get()==1&&owned(uuid).size()==1,"Same task duplicate creation/start");
                        NacosActualRpcLifecycleTest.require(results.get(0).getClass()==GrpcSdkClient.class,"Not exact actual SDK instance");
                        NacosActualRpcLifecycleTest.require(!heldWorker.get()&&!heldTransport.get(),"Single-flight startup still holds worker/admission monitor");
                    } finally {release.countDown();for(NacosActualRpcLifecycleTest.Async actor:actors)actor.thread.join(5000);}
                    return;
                }
                if(mode.equals("initialization-failure-retry")) {
                    String name=uuid+"_config-probe";
                    RpcClient failed=RpcClientFactory.createClient(name,com.alibaba.nacos.common.remote.ConnectionType.GRPC,NacosActualRpcLifecycleTest.config(name,1000));
                    captured.add(failed);RuntimeException sentinel=new IllegalStateException("synthetic-registration-failure");
                    java.lang.reflect.Field listeners=RpcClient.class.getDeclaredField("connectionEventListeners");listeners.setAccessible(true);
                    listeners.set(failed,new ArrayList<com.alibaba.nacos.common.remote.client.ConnectionEventListener>(){
                        @Override public boolean add(com.alibaba.nacos.common.remote.client.ConnectionEventListener listener){throw sentinel;}
                    });
                    Throwable thrown=null;try{ensure(transport,method,"probe");}catch(Throwable t){thrown=t;}
                    System.out.println("INSTRUMENTATION registration-list one-time failure on an actual factory-created GrpcSdkClient");
                    NacosActualRpcLifecycleTest.require(NacosActualRpcLifecycleTest.contains(thrown,sentinel),"Initial failure identity lost");
                    NacosActualRpcLifecycleTest.require(failed.isShutdown()&&RpcClientFactory.getClient(name)==null,"Failed initialization did not clean actual RPC/factory");
                    RpcClient retried=ensure(transport,method,"probe");captured.add(retried);
                    Object owners=null;try{owners=NacosActualRpcLifecycleTest.field(transport,"ownedRpcClients");}catch(NoSuchFieldException noAddedMap){}
                    System.out.println("OBSERVED retriedNewInstance="+(retried!=failed)+" running="+retried.isRunning()+" ownedRecords="+(owners==null?"no-added-map":((java.util.Set<?>)owners).size()));
                    NacosActualRpcLifecycleTest.require(retried!=failed&&retried.getClass()==GrpcSdkClient.class&&retried.isRunning(),"Retry returned poisoned or partial object");
                    NacosActualRpcLifecycleTest.require(owners==null||((java.util.Set<?>)owners).size()==1,"Successfully cleaned failed attempt still retained");
                    return;
                }
                servers.hook=()->{
                    if(!once.compareAndSet(false,true))return;
                    heldTransport.set(Thread.holdsLock(transport));heldWorker.set(Thread.holdsLock(worker));
                    entered.countDown();
                    if(mode.equals("reentry")) {
                        try{RpcClient nested=ensure(transport,method,"probe");partialReturned.set(!nested.isRunning());}
                        catch(Throwable t){nestedFailure.set(t);}
                    } else if(mode.equals("shutdown-during-start")) NacosActualRpcLifecycleTest.await(release,"release blocked startup");
                };
                creator=new NacosActualRpcLifecycleTest.Async("independent-transport-creator",()->{
                    try { outerResult.set(ensure(transport,method,"probe")); } catch(Throwable t){outerFailure.set(t);}
                });
                NacosActualRpcLifecycleTest.await(entered,"actual startup server-list callback");
                if(mode.equals("shutdown-during-start")) {
                    captured.addAll(owned(uuid));
                    NacosActualRpcLifecycleTest.Async closer=new NacosActualRpcLifecycleTest.Async("independent-transport-close",worker::shutdown);
                    closer.join(); release.countDown();
                }
                creator.join();captured.addAll(owned(uuid));
                if(outerResult.get()!=null)captured.add(outerResult.get());
                System.out.println("OBSERVED mode="+mode+" workerMonitorHeld="+heldWorker+" transportMonitorHeld="+heldTransport+" partialNestedClient="+partialReturned+" nestedFailure="+nestedFailure+" outerFailure="+outerFailure+" factory="+owned(uuid).size());
                NacosActualRpcLifecycleTest.require(servers.hookFailure.get()==null,"Server-list instrumentation failed: "+servers.hookFailure);
                NacosActualRpcLifecycleTest.require(!heldWorker.get()&&!heldTransport.get(),"External RPC startup ran under worker/admission monitor");
                if(mode.equals("reentry")) {
                    NacosActualRpcLifecycleTest.require(nestedFailure.get()!=null&&!partialReturned.get(),"Same-thread initialization reentry was not rejected without exposing partial client");
                    NacosActualRpcLifecycleTest.require(outerFailure.get()==null&&outerResult.get()!=null&&outerResult.get().isRunning(),"Independent outer startup failed");
                } else if(mode.equals("shutdown-during-start")) {
                    NacosActualRpcLifecycleTest.require(outerResult.get()==null&&outerFailure.get()!=null,"Late creator returned a client after shutdown");
                    NacosActualRpcLifecycleTest.require(owned(uuid).isEmpty(),"Late creator left factory entry");
                    for(RpcClient c:captured)NacosActualRpcLifecycleTest.require(c.isShutdown(),"Late creator retained live client");
                    NacosActualRpcLifecycleTest.require(NacosActualRpcLifecycleTest.eventually(()->peer.activeTransports.get()==0,2000),"Late actual channel leaked");
                } else {
                    NacosActualRpcLifecycleTest.require(outerFailure.get()==null&&outerResult.get()!=null&&outerResult.get().getClass()==GrpcSdkClient.class,"Expected successful exact SDK client");
                }
            } finally {
                release.countDown();servers.hook=null;
                if(creator!=null)creator.thread.join(5000);
                captured.addAll(owned(uuid));worker.shutdown();
                for(RpcClient c:captured){c.shutdown();RpcClientFactory.destroyClient(c.getName());}
                servers.shutdown();scheduler.awaitTermination(6,TimeUnit.SECONDS);
                System.out.println("FIXTURE_CLEANUP remainingFactory="+owned(uuid).size()+" schedulerTerminated="+scheduler.isTerminated());
            }
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
    void reentry() throws Exception { run("reentry"); }
    @org.junit.jupiter.api.Test
    void outside_monitor() throws Exception { run("outside-monitor"); }
    @org.junit.jupiter.api.Test
    void shutdown_during_start() throws Exception { run("shutdown-during-start"); }
    @org.junit.jupiter.api.Test
    void concurrent_same_task() throws Exception { run("concurrent-same-task"); }
    @org.junit.jupiter.api.Test
    void initialization_failure_retry() throws Exception { run("initialization-failure-retry"); }
}
