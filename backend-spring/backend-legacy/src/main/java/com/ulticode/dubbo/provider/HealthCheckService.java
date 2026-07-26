package com.ulticode.dubbo.provider;

/**
 * P1-INFRA-003: placeholder health-check Contract.
 *
 * <p>The Triple + Nacos registry wiring is verified by exporting at least
 * one {@code @DubboService} so Dubbo's {@code ServiceConfig#export()}
 * path runs end-to-end. Without any exported service, Dubbo never
 * creates a Nacos registry client and the instance never lands in
 * Nacos — see Dubbo 3.3.6
 * {@code ServiceConfig#doExportUrlFor1Protocol} / {@code RegistryProtocol}.
 *
 * <p>Phase 4 (P4-RPC-001 / P4-RPC-002) will replace this placeholder
 * with the real provider-owned contracts from backend-auth-api and
 * backend-app-api. The shape here is deliberately trivial so it cannot
 * drift into a real business interface.
 */
public interface HealthCheckService {

    /**
     * Liveness probe for the Dubbo registry wiring.
     *
     * @return the literal string {@code "pong"} so smoke tests can
     *         assert end-to-end without coupling to backend-legacy's
     *         business logic.
     */
    String ping();
}
