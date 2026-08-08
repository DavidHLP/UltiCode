package com.ulticode.admin.dubbo.provider;

import com.ulticode.common.health.RpcHealthService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * P1-INFRA-005: admin shell RPC health provider.
 */
@DubboService(group = "backend-admin", version = "1.0.0")
public class AdminRpcHealthProvider implements RpcHealthService {

    @Override
    public String ping(String caller) {
        return "pong-admin:" + caller;
    }
}
