package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Admin consumer adapter for the App-owned subscription read contract.
 *
 * <p>The implementation is intentionally a one-hop read seam: Admin keeps
 * its analytics projections local while App remains the subscription owner.
 */
@Primary
@Component
public class DubboSubscriptionReadAdapter implements SubscriptionReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubscriptionReadPort subscriptionReadPort;

    @Override
    public long countActiveSubscriptions() {
        return subscriptionReadPort.countActiveSubscriptions();
    }

    @Override
    public java.util.List<String> listActiveSubscriptionPlans() {
        return subscriptionReadPort.listActiveSubscriptionPlans();
    }
}
