package com.ulticode.app.subscription.provider;

import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionReadAdapterTest {

    @Mock
    private SubscriptionMapper subscriptionMapper;

    private SubscriptionReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SubscriptionReadAdapter(subscriptionMapper);
    }

    @Test
    void countActiveSubscriptionsDelegatesToActiveQuery() {
        when(subscriptionMapper.selectCount(any())).thenReturn(3L);

        assertThat(adapter.countActiveSubscriptions()).isEqualTo(3L);
    }

    @Test
    void listActiveSubscriptionPlansMapsOnlyPlanNames() {
        Subscription monthly = new Subscription();
        monthly.setPlan("PREMIUM_MONTHLY");
        Subscription yearly = new Subscription();
        yearly.setPlan("PREMIUM_YEARLY");
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(monthly, yearly));

        assertThat(adapter.listActiveSubscriptionPlans())
                .containsExactly("PREMIUM_MONTHLY", "PREMIUM_YEARLY");
    }
}
