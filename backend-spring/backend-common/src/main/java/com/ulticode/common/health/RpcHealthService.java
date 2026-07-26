package com.ulticode.common.health;

/**
 * P1-INFRA-005: minimal RPC health ping used to prove service discovery.
 *
 * <p>Each service implements this with a distinct Dubbo group so the Nacos
 * registry shows three separate services during the Phase 1 shell smoke test.
 * This is a placeholder contract; real health checks remain
 * {@code /actuator/health}.
 */
public interface RpcHealthService {

    /**
     * Returns a short identity string from the provider instance.
     *
     * @param caller an arbitrary caller label
     * @return {@code pong-<service-name>} so the smoke can verify routing
     */
    String ping(String caller);
}
