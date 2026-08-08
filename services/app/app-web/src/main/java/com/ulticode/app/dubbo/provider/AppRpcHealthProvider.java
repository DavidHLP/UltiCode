package com.ulticode.app.dubbo.provider;

import com.ulticode.common.health.RpcHealthService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * P1-INFRA-005: app shell RPC health provider.
 */
@DubboService(group = "backend-app", version = "1.0.0")
public class AppRpcHealthProvider implements RpcHealthService {

    @Override
    public String ping(String caller) {
        return "pong-app:" + caller;
    }
}
