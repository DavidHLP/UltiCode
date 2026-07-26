package com.ulticode.auth.dubbo.provider;

import com.ulticode.common.health.RpcHealthService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * P1-INFRA-005: auth shell RPC health provider.
 *
 * <p>Only exists to register a Dubbo service instance in Nacos so the
 * Phase 1 smoke can assert that backend-auth is discoverable. No business
 * logic is migrated here.
 */
@DubboService(group = "backend-auth", version = "1.0.0")
public class AuthRpcHealthProvider implements RpcHealthService {

    @Override
    public String ping(String caller) {
        return "pong-auth:" + caller;
    }
}
