package com.ulticode.rpc.resilience;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistryDirectory;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;
import org.apache.dubbo.registry.client.migration.MigrationInvoker;
import org.apache.dubbo.registry.client.migration.ServiceDiscoveryMigrationInvoker;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;
import org.junit.jupiter.api.Test;

import static com.ulticode.rpc.resilience.DubboDiscoveryReadiness.*;
import static org.assertj.core.api.Assertions.assertThat;

class DubboDiscoveryReadinessTest {
    private static final String INTERFACE = Runnable.class.getName();
    private static final URL URL_VALUE = URL.valueOf("consumer://localhost/" + INTERFACE);

    @Test
    void missingManagerReferenceConfigAndInvokerRemainDistinct() {
        assertThat(inspectApplication(null, INTERFACE).directory().state()).isEqualTo(State.MANAGER_MISSING);
        assertThat(inspectApplication(new ReferenceBeanManager(), INTERFACE).directory().state())
                .isEqualTo(State.REFERENCE_MISSING);
        ReferenceBean<Object> reference = reference(null);
        ReferenceBeanManager manager = manager(reference);
        assertThat(inspectApplication(manager, INTERFACE).directory().state()).isEqualTo(State.CONFIG_MISSING);
        assertThat(inspectApplication(manager((Invoker<?>) null), INTERFACE).directory().state())
                .isEqualTo(State.INVOKER_MISSING);
    }

    @Test
    void bothMigrationTypesUseOnlyApplicationChildEvenWhenSelectedDirectoryWouldFail() {
        for (boolean discoveryOnly : List.of(false, true)) {
            MigrationInvoker<Object> migration = migration(discoveryOnly);
            assertThat(inspectApplication(manager(migration), INTERFACE).directory().state())
                    .isEqualTo(State.INVOKER_MISSING);
            migration.setServiceDiscoveryInvoker(cluster(new TestDirectory(null)));
            var result = inspectApplication(manager(migration), INTERFACE);
            assertThat(result.directory().directoryKind()).isEqualTo(DirectoryKind.SERVICE_DISCOVERY);
            assertThat(result.directory().invokerKind()).isEqualTo(discoveryOnly
                    ? InvokerKind.SERVICE_DISCOVERY_MIGRATION : InvokerKind.MIGRATION);
            assertThat(result.listenerState()).isEqualTo(ListenerState.MISSING);
        }
    }

    @Test
    void existingListenerReportsEmptyMapEmptyListAndInstancesWithoutReadingNamesOrObjects() {
        for (Map<String, List<ServiceInstance>> data : List.of(
                Map.<String, List<ServiceInstance>>of(), Map.of("secret-host", List.<ServiceInstance>of()),
                Map.of("secret-host", sized(2), "secret-token", sized(3)))) {
            var result = observe(listener(Set.of("private-provider"), data));
            assertThat(result.listenerState()).isEqualTo(ListenerState.PRESENT);
            assertThat(result.mappedApplications().token()).isEqualTo("1");
            assertThat(result.observedApplications().token()).isEqualTo(Integer.toString(data.size()));
            assertThat(result.instances().token()).isEqualTo(data.size() == 2 ? "5" : "0");
            assertThat(result.toString()).doesNotContain("secret", "private-provider", "consumer://");
        }
        var empty = observe(listener(Set.of(), Map.of()));
        assertThat(empty.mappedApplications().token()).isEqualTo("0");
    }

    @Test
    void countsAreBoundedAndUnknownOrExceptionsAreSanitized() {
        assertThat(observe(listener(Set.of("x"), Map.of("x", sized(10_001)))).instances().token())
                .isEqualTo("OVERFLOW");
        assertThat(observe(listener(Set.of("x"), Map.of("x", sized(6_000), "y", sized(6_000))))
                .instances().token()).isEqualTo("OVERFLOW");
        assertThat(observe(listener(Set.of("x"), Map.of("x", sized(-1)))).instances().token()).isEqualTo("ERROR");
        assertThat(observe(listener(Set.of("x"), null)).instances().token()).isEqualTo("UNKNOWN");
        ServiceInstancesChangedListener failing = new ServiceInstancesChangedListener(Set.of(), null) {
            @Override public Map<String, List<ServiceInstance>> getAllInstances() {
                throw new IllegalStateException("secret-token private-url");
            }
        };
        var error = observe(failing);
        assertThat(error.listenerState()).isEqualTo(ListenerState.ERROR);
        assertThat(error.directory().state()).isEqualTo(State.INVOKER_PRESENT);
        assertThat(error.toString()).doesNotContain("secret", "private-url");
    }

    @Test
    void applicationAndInstanceCountsBoundTraversalAndSanitizeCollectionFailures() {
        Set<String> oversizedNames = new AbstractSet<>() {
            @Override public int size() { return 10_001; }
            @Override public Iterator<String> iterator() { throw new AssertionError("Names must not be read"); }
        };
        assertThat(observe(listener(oversizedNames, Map.of())).mappedApplications().token()).isEqualTo("OVERFLOW");
        Map<String, List<ServiceInstance>> oversizedMap = new AbstractMap<>() {
            @Override public Set<Entry<String, List<ServiceInstance>>> entrySet() {
                return new AbstractSet<>() {
                    @Override public int size() { return 10_001; }
                    @Override public Iterator<Entry<String, List<ServiceInstance>>> iterator() {
                        return new Iterator<>() {
                            int visited;
                            @Override public boolean hasNext() { return true; }
                            @Override public Entry<String, List<ServiceInstance>> next() {
                                if (++visited > 10_001) throw new AssertionError("Unbounded traversal");
                                return Map.entry("private-name", List.of());
                            }
                        };
                    }
                };
            }
        };
        var overflow = observe(listener(Set.of(), oversizedMap));
        assertThat(overflow.observedApplications().token()).isEqualTo("OVERFLOW");
        assertThat(overflow.instances().token()).isEqualTo("OVERFLOW");
        List<ServiceInstance> failingList = new AbstractList<>() {
            @Override public int size() { throw new IllegalStateException("private-url"); }
            @Override public ServiceInstance get(int index) { throw new AssertionError("Instance access forbidden"); }
        };
        var failure = observe(listener(Set.of(), Map.of("private-name", failingList)));
        assertThat(failure.instances().token()).isEqualTo("ERROR");
        assertThat(failure.toString()).doesNotContain("private");
    }

    @Test
    void listenerGetterFailurePreservesDirectoryCounts() {
        TestDirectory directory = new TestDirectory(null) {
            @Override public ServiceInstancesChangedListener getServiceListener() {
                throw new IllegalStateException("private-url");
            }
        };
        var result = inspectApplication(manager(cluster(directory)), INTERFACE);
        assertThat(result.directory().all().token()).isEqualTo("0");
        assertThat(result.listenerState()).isEqualTo(ListenerState.ERROR);
        assertThat(result.toString()).doesNotContain("private");
    }

    @Test
    void getterFailuresAndUnsupportedInvokerDoNotEscape() {
        ClusterInvoker<Object> unsupported = cluster(null);
        assertThat(inspectApplication(manager(unsupported), INTERFACE).directory().directoryKind())
                .isEqualTo(DirectoryKind.MISSING);
        ReferenceBeanManager failing = new ReferenceBeanManager() {
            @Override public Collection<ReferenceBean> getReferences() { throw new IllegalStateException("secret"); }
        };
        assertThat(inspectApplication(failing, INTERFACE).directory().state()).isEqualTo(State.INSPECTION_ERROR);
    }

    private static ApplicationObservation observe(ServiceInstancesChangedListener listener) {
        return inspectApplication(manager(cluster(new TestDirectory(listener))), INTERFACE);
    }

    private static ServiceInstancesChangedListener listener(Set<String> names, Map<String, List<ServiceInstance>> data) {
        return new ServiceInstancesChangedListener(names, null) {
            @Override public Map<String, List<ServiceInstance>> getAllInstances() { return data; }
        };
    }

    private static List<ServiceInstance> sized(int size) {
        return new AbstractList<>() {
            @Override public int size() { return size; }
            @Override public ServiceInstance get(int index) { throw new AssertionError("Must not inspect instances"); }
        };
    }

    private static ReferenceBean<Object> reference(ReferenceConfig<Object> config) {
        return new ReferenceBean<>() {
            @Override public String getServiceInterface() { return INTERFACE; }
            @Override public ReferenceConfig getReferenceConfig() { return config; }
            @Override public Object getObject() { throw new AssertionError("Must not initialize proxy"); }
        };
    }

    private static ReferenceBeanManager manager(Invoker<?> invoker) {
        return manager(reference(new ReferenceConfig<>() {
            @Override public Invoker<?> getInvoker() { return invoker; }
        }));
    }

    private static ReferenceBeanManager manager(ReferenceBean<?> reference) {
        return new ReferenceBeanManager() {
            @Override public Collection<ReferenceBean> getReferences() { return List.of(reference); }
        };
    }

    private static MigrationInvoker<Object> migration(boolean discoveryOnly) {
        if (discoveryOnly) return new ServiceDiscoveryMigrationInvoker<>(null, null, null, Object.class, URL_VALUE, URL_VALUE) {
            @Override public Directory<Object> getDirectory() { throw new AssertionError("Selected directory forbidden"); }
            @Override public boolean isAvailable() { throw new AssertionError("Probe forbidden"); }
            @Override public URL getUrl() { throw new AssertionError("URL forbidden"); }
            @Override public Result invoke(Invocation invocation) { throw new AssertionError("RPC forbidden"); }
        };
        return new MigrationInvoker<>(null, null, null, Object.class, URL_VALUE, URL_VALUE) {
            @Override public Directory<Object> getDirectory() { throw new AssertionError("Selected directory forbidden"); }
            @Override public boolean isAvailable() { throw new AssertionError("Probe forbidden"); }
            @Override public URL getUrl() { throw new AssertionError("URL forbidden"); }
            @Override public Result invoke(Invocation invocation) { throw new AssertionError("RPC forbidden"); }
        };
    }

    private static ClusterInvoker<Object> cluster(Directory<Object> directory) {
        return new ClusterInvoker<>() {
            @Override public Directory<Object> getDirectory() { return directory; }
            @Override public Class<Object> getInterface() { return Object.class; }
            @Override public boolean isDestroyed() { return false; }
            @Override public void destroy() { throw new AssertionError("Destroy forbidden"); }
            @Override public URL getRegistryUrl() { throw new AssertionError("Registry URL forbidden"); }
            @Override public URL getUrl() { throw new AssertionError("URL forbidden"); }
            @Override public boolean isAvailable() { throw new AssertionError("Probe forbidden"); }
            @Override public Result invoke(Invocation invocation) { throw new AssertionError("RPC forbidden"); }
        };
    }

    private static class TestDirectory extends ServiceDiscoveryRegistryDirectory<Object> {
        private final ServiceInstancesChangedListener listener;
        private boolean armed;
        TestDirectory(ServiceInstancesChangedListener listener) { super(Object.class, URL_VALUE); this.listener = listener; this.armed = true; }
        @Override public URL getUrl() {
            if (armed) throw new AssertionError("Directory URL forbidden");
            return super.getUrl();
        }
        @Override public ServiceInstancesChangedListener getServiceListener() { return listener; }
        @Override public List<Invoker<Object>> getAllInvokers() { return List.of(); }
        @Override public org.apache.dubbo.rpc.cluster.router.state.BitList<Invoker<Object>> getValidInvokers() { return null; }
        @Override public boolean isAvailable() { throw new AssertionError("Probe forbidden"); }
    }
}
