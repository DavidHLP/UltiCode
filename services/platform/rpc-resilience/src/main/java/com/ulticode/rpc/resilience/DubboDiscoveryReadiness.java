package com.ulticode.rpc.resilience;

import java.util.Collection;
import java.util.Map;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;
import java.util.function.Supplier;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistryDirectory;
import org.apache.dubbo.registry.client.migration.MigrationInvoker;
import org.apache.dubbo.registry.client.migration.ServiceDiscoveryMigrationInvoker;
import org.apache.dubbo.registry.integration.DynamicDirectory;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import java.util.HashSet;
import java.util.Set;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;

/** Existing readiness predicate plus metadata-only diagnostics; neither invokes business RPC. */
public final class DubboDiscoveryReadiness {
    private DubboDiscoveryReadiness() {}

    /** Diagnostic metadata only. Never probe availability or stringify Dubbo objects. */
    public enum State {
        MANAGER_MISSING, REFERENCE_MISSING, CONFIG_MISSING, INVOKER_MISSING,
        INVOKER_PRESENT, UNSUPPORTED, INSPECTION_ERROR
    }

    public enum InvokerKind { SERVICE_DISCOVERY_MIGRATION, MIGRATION, CLUSTER, OTHER, MISSING }
    public enum DirectoryKind { SERVICE_DISCOVERY, DYNAMIC, OTHER, MISSING }
    public enum Destroyed { TRUE, FALSE, UNKNOWN, ERROR }
    public enum CountState { MEASURED, UNKNOWN, OVERFLOW, ERROR }

    public record Count(CountState state, int value) {
        public Count {
            if (state == null || value < 0 || value > 10_000) throw new IllegalArgumentException();
        }

        public String token() {
            return state == CountState.MEASURED ? Integer.toString(value) : state.name();
        }
    }

    public record Observation(State state, InvokerKind invokerKind, DirectoryKind directoryKind,
                              Destroyed destroyed, Count all, Count valid, Count reconnect, Count disabled) {}

    private static final Count UNKNOWN = new Count(CountState.UNKNOWN, 0);

    private static Observation absent(State state, InvokerKind invoker, DirectoryKind directory) {
        return new Observation(state, invoker, directory, Destroyed.UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
    }

    public static Observation inspect(ReferenceBeanManager manager, String requiredInterface) {
        if (manager == null) return absent(State.MANAGER_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING);
        try {
            for (var reference : manager.getReferences()) {
                if (!requiredInterface.equals(reference.getServiceInterface())) continue;
                var config = reference.getReferenceConfig();
                if (config == null) return absent(State.CONFIG_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING);
                return inspectInvoker(config.getInvoker());
            }
            return absent(State.REFERENCE_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING);
        } catch (RuntimeException notInitialized) {
            return absent(State.INSPECTION_ERROR, InvokerKind.MISSING, DirectoryKind.MISSING);
        }
    }

    private static Observation inspectInvoker(Invoker<?> invoker) {
        if (invoker == null) return absent(State.INVOKER_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING);
        InvokerKind kind = invoker instanceof ServiceDiscoveryMigrationInvoker<?> ? InvokerKind.SERVICE_DISCOVERY_MIGRATION
                : invoker instanceof MigrationInvoker<?> ? InvokerKind.MIGRATION
                : invoker instanceof ClusterInvoker<?> ? InvokerKind.CLUSTER : InvokerKind.OTHER;
        if (!(invoker instanceof ClusterInvoker<?> cluster)) {
            return absent(State.UNSUPPORTED, kind, DirectoryKind.MISSING);
        }
        try {
            // MigrationInvoker delegates this getter without checking availability.
            // Do not traverse its children or call isAvailable(), list(), refresh(), or getUrl().
            var directory = cluster.getDirectory();
            if (directory == null) return absent(State.INVOKER_PRESENT, kind, DirectoryKind.MISSING);
            if (!(directory instanceof DynamicDirectory<?> dynamic)) {
                return absent(State.UNSUPPORTED, kind, DirectoryKind.OTHER);
            }
            DirectoryKind directoryKind = dynamic instanceof ServiceDiscoveryRegistryDirectory<?>
                    ? DirectoryKind.SERVICE_DISCOVERY : DirectoryKind.DYNAMIC;
            // These getters are non-atomic observations; counts have no equality/readiness invariant.
            return new Observation(State.INVOKER_PRESENT, kind, directoryKind, destroyed(dynamic),
                    count(dynamic::getAllInvokers), count(dynamic::getValidInvokers),
                    count(dynamic::getInvokersToReconnect), count(dynamic::getDisabledInvokers));
        } catch (RuntimeException notInitialized) {
            return absent(State.INSPECTION_ERROR, kind, DirectoryKind.MISSING);
        }
    }

    public enum ListenerState { MISSING, PRESENT, UNSUPPORTED, ERROR }

    /** Existing application-discovery child only, independent of the selected migration branch. */
    public record ApplicationObservation(Observation directory, ListenerState listenerState,
                                         Count mappedApplications, Count observedApplications, Count instances) {}

    private static ApplicationObservation applicationAbsent(Observation directory, ListenerState listener) {
        return new ApplicationObservation(directory, listener, UNKNOWN, UNKNOWN, UNKNOWN);
    }

    public static ApplicationObservation inspectApplication(ReferenceBeanManager manager, String requiredInterface) {
        if (manager == null) return applicationAbsent(
                absent(State.MANAGER_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING), ListenerState.MISSING);
        try {
            for (var reference : manager.getReferences()) {
                if (!requiredInterface.equals(reference.getServiceInterface())) continue;
                var config = reference.getReferenceConfig();
                if (config == null) return applicationAbsent(
                        absent(State.CONFIG_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING), ListenerState.MISSING);
                return inspectApplicationInvoker(config.getInvoker());
            }
            return applicationAbsent(absent(State.REFERENCE_MISSING, InvokerKind.MISSING, DirectoryKind.MISSING),
                    ListenerState.MISSING);
        } catch (RuntimeException notInitialized) {
            return applicationAbsent(absent(State.INSPECTION_ERROR, InvokerKind.MISSING, DirectoryKind.MISSING),
                    ListenerState.ERROR);
        }
    }

    private static ApplicationObservation inspectApplicationInvoker(Invoker<?> invoker) {
        InvokerKind kind = invoker instanceof ServiceDiscoveryMigrationInvoker<?> ? InvokerKind.SERVICE_DISCOVERY_MIGRATION
                : invoker instanceof MigrationInvoker<?> ? InvokerKind.MIGRATION
                : invoker instanceof ClusterInvoker<?> ? InvokerKind.CLUSTER
                : invoker == null ? InvokerKind.MISSING : InvokerKind.OTHER;
        try {
            // ServiceDiscoveryMigrationInvoker also stores its application invoker in this inherited field.
            // Never use the selected branch, initialize a reference, or probe transport availability.
            Invoker<?> application = invoker instanceof MigrationInvoker<?> migration
                    ? migration.getServiceDiscoveryInvoker() : invoker;
            if (application == null) return applicationAbsent(
                    absent(State.INVOKER_MISSING, kind, DirectoryKind.MISSING), ListenerState.MISSING);
            if (!(application instanceof ClusterInvoker<?> cluster)) return applicationAbsent(
                    absent(State.UNSUPPORTED, kind, DirectoryKind.MISSING), ListenerState.UNSUPPORTED);
            var directory = cluster.getDirectory();
            if (directory == null) return applicationAbsent(
                    absent(State.INVOKER_PRESENT, kind, DirectoryKind.MISSING), ListenerState.MISSING);
            if (!(directory instanceof ServiceDiscoveryRegistryDirectory<?> dynamic)) return applicationAbsent(
                    absent(State.UNSUPPORTED, kind, directory instanceof DynamicDirectory<?>
                            ? DirectoryKind.DYNAMIC : DirectoryKind.OTHER), ListenerState.UNSUPPORTED);
            Observation observation = new Observation(State.INVOKER_PRESENT, kind, DirectoryKind.SERVICE_DISCOVERY,
                    destroyed(dynamic), count(dynamic::getAllInvokers), count(dynamic::getValidInvokers),
                    count(dynamic::getInvokersToReconnect), count(dynamic::getDisabledInvokers));
            try {
                ServiceInstancesChangedListener listener = dynamic.getServiceListener();
                if (listener == null) return applicationAbsent(observation, ListenerState.MISSING);
                Count mapped = count(listener::getServiceNames);
                // These are bounded, non-atomic samples. A concurrent update can produce ERROR;
                // absence/zero alone does not prove a missing mapping or explain an RPC failure.
                Map<?, ? extends Collection<?>> instances = listener.getAllInstances();
                Count observed = instances == null ? UNKNOWN : count(instances::values);
                return new ApplicationObservation(observation, ListenerState.PRESENT, mapped, observed,
                        instanceCount(instances));
            } catch (RuntimeException notInitialized) {
                return applicationAbsent(observation, ListenerState.ERROR);
            }
        } catch (RuntimeException notInitialized) {
            return applicationAbsent(absent(State.INSPECTION_ERROR, kind, DirectoryKind.MISSING), ListenerState.ERROR);
        }
    }

    private static Count instanceCount(Map<?, ? extends Collection<?>> instances) {
        if (instances == null) return UNKNOWN;
        try {
            int total = 0;
            int visited = 0;
            for (Collection<?> values : instances.values()) {
                if (++visited > 10_000) return new Count(CountState.OVERFLOW, 0);
                if (values == null) return UNKNOWN;
                int size = values.size();
                if (size < 0) return new Count(CountState.ERROR, 0);
                if (size > 10_000 - total) return new Count(CountState.OVERFLOW, 0);
                total += size;
            }
            return new Count(CountState.MEASURED, total);
        } catch (RuntimeException notInitialized) {
            return new Count(CountState.ERROR, 0);
        }
    }

    private static Count count(Supplier<? extends Collection<?>> getter) {
        try {
            Collection<?> values = getter.get();
            if (values == null) return UNKNOWN;
            int size = values.size();
            if (size < 0) return new Count(CountState.ERROR, 0);
            return size > 10_000 ? new Count(CountState.OVERFLOW, 0) : new Count(CountState.MEASURED, size);
        } catch (RuntimeException notInitialized) {
            return new Count(CountState.ERROR, 0);
        }
    }

    private static Destroyed destroyed(DynamicDirectory<?> directory) {
        try {
            return directory.isDestroyed() ? Destroyed.TRUE : Destroyed.FALSE;
        } catch (RuntimeException notInitialized) {
            return Destroyed.ERROR;
        }
    }

    public static boolean available(ReferenceBeanManager manager, Set<String> requiredInterfaces) {
        if (manager == null || requiredInterfaces.isEmpty()) return false;
        Set<String> available = new HashSet<>();
        try {
            for (var reference : manager.getReferences()) {
                if (!requiredInterfaces.contains(reference.getServiceInterface())) continue;
                var config = reference.getReferenceConfig();
                if (config != null && config.getInvoker() != null && config.getInvoker().isAvailable()) {
                    available.add(reference.getServiceInterface());
                }
            }
            return available.containsAll(requiredInterfaces);
        } catch (RuntimeException notInitialized) {
            return false;
        }
    }
}
