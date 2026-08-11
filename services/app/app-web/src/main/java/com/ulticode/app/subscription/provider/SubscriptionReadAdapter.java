package com.ulticode.app.subscription.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * App-side implementation of {@link SubscriptionReadPort}.
 *
 * <p>Backs the admin analytics read path after the subscription family
 * relocated from backend-legacy to backend-app (P7-APP-SUBSCRIPTION-001).
 * The admin module consumes the port interface from {@code backend-app-api}
 * and never touches {@link SubscriptionMapper} or {@link Subscription}
 * directly.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubscriptionReadAdapter implements SubscriptionReadPort {

    private final SubscriptionMapper subscriptionMapper;

    @Override
    public long countActiveSubscriptions() {
        LambdaQueryWrapper<Subscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Subscription::getStatus, SubscriptionStatus.ACTIVE.getValue());
        return subscriptionMapper.selectCount(wrapper);
    }

    @Override
    public List<String> listActiveSubscriptionPlans() {
        LambdaQueryWrapper<Subscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Subscription::getStatus, SubscriptionStatus.ACTIVE.getValue());
        return subscriptionMapper.selectList(wrapper).stream()
                .map(Subscription::getPlan)
                .collect(Collectors.toList());
    }
}
