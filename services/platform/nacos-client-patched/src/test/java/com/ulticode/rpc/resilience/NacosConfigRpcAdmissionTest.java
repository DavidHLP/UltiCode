package com.ulticode.rpc.resilience;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigServerListManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcSdkClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/** Actual ConfigWorker/transport/security/server-list constructors with a synthetic loopback protocol peer.
 * No user config or credential is supplied; no authentication or real registry behavior is claimed.
 */
@org.junit.jupiter.api.parallel.ResourceLock("nacos-rpc-fixture")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
final class NacosConfigRpcAdmissionTest {
    static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
    static Map<String,RpcClient> clients(String prefix){Map<String,RpcClient> r=new HashMap<>();RpcClientFactory.getAllClientEntries().forEach(e->{if(e.getKey().startsWith(prefix))r.put(e.getKey(),e.getValue());});return r;}
    static boolean registered(Object subscriber) throws Exception {
        Map<?,?> registrations=(Map<?,?>)NacosActualRpcLifecycleTest.field(NotifyCenter.getSharePublisher(),"subMappings");
        return registrations.values().stream().anyMatch(s->((Set<?>)s).contains(subscriber));
    }
    static void run(String scenario) throws Exception {
        try(NacosActualRpcLifecycleTest.LocalProtocolServer peer=new NacosActualRpcLifecycleTest.LocalProtocolServer(false,false)) {
            Properties p=new Properties();
            p.setProperty(PropertyKeyConst.SERVER_ADDR,"127.0.0.1:"+peer.port());
            p.setProperty(PropertyKeyConst.NAMESPACE,"synthetic-local-fixture");
            p.setProperty(PropertyKeyConst.USERNAME,"");p.setProperty(PropertyKeyConst.PASSWORD,"");
            p.setProperty(PropertyKeyConst.ENABLE_CLIENT_METRICS,"false");
            p.setProperty(PropertyKeyConst.CLIENT_WORKER_THREAD_COUNT,"2");
            NacosClientProperties props=NacosClientProperties.PROTOTYPE.derive(p);
            ConfigServerListManager servers=new ConfigServerListManager(props);servers.start();
            ClientWorker worker=new ClientWorker(new ConfigFilterChainManager(p),servers,props);
            String uuid=(String)NacosActualRpcLifecycleTest.field(worker,"uuid");
            Object transport=NacosActualRpcLifecycleTest.field(worker,"agent");
            ScheduledExecutorService main=(ScheduledExecutorService)NacosActualRpcLifecycleTest.field(transport,"executor");
            Method ensure=transport.getClass().getDeclaredMethod("ensureRpcClient",String.class);ensure.setAccessible(true);
            List<RpcClient> observed=new ArrayList<>();
            List<Subscriber> subscribers=new ArrayList<>();
            Throwable primaryFailure = null;
            try {
                require(worker.isHealthServer(),"Initial actual worker RPC connection failed");
                RpcClient before=clients(uuid).values().iterator().next();observed.add(before);
                require(before.getClass()==GrpcSdkClient.class,"RPC object is not actual GrpcSdkClient");
                subscribers.add((Subscriber)NacosActualRpcLifecycleTest.field(transport,"subscriber"));
                if(scenario.equals("multiple-subscribers")) {
                    observed.add((RpcClient)ensure.invoke(transport,"1"));
                    subscribers.add((Subscriber)NacosActualRpcLifecycleTest.field(transport,"subscriber"));
                    require(subscribers.get(0)!=subscribers.get(1),"Fixture did not create distinct subscribers");
                    require(registered(subscribers.get(0)) && registered(subscribers.get(1)),"Both subscribers must be registered before shutdown");
                }
                worker.shutdown();
                // Observe SDK-owned resources before any test fallback cleanup can affect them.
                for (RpcClient client : observed) NacosActualRpcLifecycleTest.assertClosed(client, peer);
                if(scenario.equals("multiple-subscribers")) {
                    boolean first=registered(subscribers.get(0)),second=registered(subscribers.get(1));
                    System.out.println("OBSERVED scenario="+scenario+" firstSubscriberRetained="+first+" lastSubscriberRetained="+second+" factory="+clients(uuid).size());
                    require(!first && !second,"Actual NotifyCenter retained an earlier owned RPC subscriber after shutdown");
                    return;
                }
                require(clients(uuid).isEmpty(),"Initial shutdown did not remove original factory entry");
                require(before.isShutdown(),"Initial shutdown did not close original RPC");
                boolean status=false;Throwable rejected=null;
                try {
                    if(scenario.equals("post-close-status"))status=worker.isHealthServer();
                    else ensure.invoke(transport,"new-after-close");
                } catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause();}
                Map<String,RpcClient> after=clients(uuid);observed.addAll(after.values());
                System.out.println("OBSERVED scenario="+scenario+" healthAfterClose="+status+" factoryAfterClose="+after.size()+" checks="+peer.checks+" activeTransports="+peer.activeTransports+" schedulerClosed="+main.isShutdown()+" rejected="+rejected);
                for(RpcClient c:after.values())System.out.println("LATE_CLIENT "+c.getName()+" class="+c.getClass().getName()+" running="+c.isRunning()+" sameAsBefore="+(c==before));
                require(after.isEmpty(),"Closed actual ConfigRpcTransportClient created/started new RPC factory entry");
                require(!status,"Closed worker reports healthy by creating a new RPC");
            } catch (Exception | Error failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                // These finalizers happen only after measurements and assertions. They never count as SDK pass evidence.
                java.util.List<NacosActualRpcLifecycleTest.Checked> cleanup = new ArrayList<>();
                cleanup.add(worker::shutdown);
                for(RpcClient c:observed) {
                    cleanup.add(c::shutdown);
                    cleanup.add(()->RpcClientFactory.destroyClient(c.getName()));
                }
                for(Subscriber subscriber:subscribers) cleanup.add(()->NotifyCenter.deregisterSubscriber(subscriber));
                cleanup.add(servers::shutdown);
                cleanup.add(()->require(main.awaitTermination(8,TimeUnit.SECONDS),"Fixture worker scheduler did not terminate"));
                cleanup.add(()->require(NacosActualRpcLifecycleTest.eventually(()->peer.activeTransports.get()==0,3000),"Fixture channels did not terminate"));
                Throwable cleanupFailure = null;
                for(NacosActualRpcLifecycleTest.Checked step:cleanup) {
                    try { step.run(); } catch(Throwable failure) {
                        if(cleanupFailure==null) cleanupFailure=failure;
                        else if(cleanupFailure!=failure) cleanupFailure.addSuppressed(failure);
                    }
                }
                if(cleanupFailure!=null) {
                    if(primaryFailure!=null) primaryFailure.addSuppressed(cleanupFailure);
                    else if(cleanupFailure instanceof Exception) throw (Exception)cleanupFailure;
                    else throw (Error)cleanupFailure;
                }
                System.out.println("FIXTURE_CLEANUP factory="+clients(uuid).size()+" transports="+peer.activeTransports);
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
    void post_close_status() throws Exception { run("post-close-status"); }
    @org.junit.jupiter.api.Test
    void post_close_ensure() throws Exception { run("post-close-ensure"); }
    @org.junit.jupiter.api.Test
    void multiple_subscribers() throws Exception { run("multiple-subscribers"); }
}
