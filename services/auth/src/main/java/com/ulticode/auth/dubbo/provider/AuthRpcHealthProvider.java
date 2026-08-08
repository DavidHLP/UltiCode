package com.ulticode.auth.dubbo.provider;

import com.ulticode.common.health.RpcHealthService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo health provider for the populated backend-auth service.
 *
 * <p>It keeps service discovery and process-level readiness independent from
 * the Auth business providers while the authentication, authorization, OAuth,
 * credential, and session capabilities remain available through their
 * owner-specific adapters.</p>
 */
@DubboService(group = "backend-auth", version = "1.0.0")
public class AuthRpcHealthProvider implements RpcHealthService {

    @Override
    public String ping(String caller) {
        return "pong-auth:" + caller;
    }
}
