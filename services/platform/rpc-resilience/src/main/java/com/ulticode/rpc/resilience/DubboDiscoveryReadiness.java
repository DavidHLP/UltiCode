package com.ulticode.rpc.resilience;

import java.util.HashSet;
import java.util.Set;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;

/** Read-only discovery check: never invokes a query or retries a command. */
public final class DubboDiscoveryReadiness {
    private DubboDiscoveryReadiness() {}

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
