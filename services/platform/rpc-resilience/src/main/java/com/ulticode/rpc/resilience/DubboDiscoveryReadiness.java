package com.ulticode.rpc.resilience;

import java.util.HashSet;
import java.util.Set;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;

/** Read-only discovery check: never invokes a query or retries a command. */
public final class DubboDiscoveryReadiness {
    private DubboDiscoveryReadiness() {}

    /** Diagnostic only; never used to decide readiness or expose exception text. */
    public enum State {
        MANAGER_MISSING, REFERENCE_MISSING, CONFIG_MISSING, INVOKER_MISSING,
        UNAVAILABLE, INSPECTION_ERROR, AVAILABLE
    }

    public static State inspect(ReferenceBeanManager manager, String requiredInterface) {
        if (manager == null) return State.MANAGER_MISSING;
        State state = State.REFERENCE_MISSING;
        try {
            for (var reference : manager.getReferences()) {
                if (!requiredInterface.equals(reference.getServiceInterface())) continue;
                var config = reference.getReferenceConfig();
                if (config == null) {
                    state = State.CONFIG_MISSING;
                } else {
                    var invoker = config.getInvoker();
                    if (invoker == null) state = State.INVOKER_MISSING;
                    else if (invoker.isAvailable()) return State.AVAILABLE;
                    else state = State.UNAVAILABLE;
                }
            }
            return state;
        } catch (RuntimeException notInitialized) {
            return State.INSPECTION_ERROR;
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
