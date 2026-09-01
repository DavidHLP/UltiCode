package com.ulticode.app.config;

import com.ulticode.common.redis.RedisWorkloadRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AppRedisRoleConfigTest {

    @Test
    void roleAliasesDelegateToTheCurrentConnectionFactory() {
        RedisConnectionFactory delegate = mock(RedisConnectionFactory.class);

        RedisConnectionFactory selected = new AppRedisRoleConfig()
                .roleConnectionFactory(delegate);

        assertThat(selected).isSameAs(delegate);
        assertThat(RedisWorkloadRole.values())
                .containsExactly(
                        RedisWorkloadRole.STREAMS,
                        RedisWorkloadRole.CACHE,
                        RedisWorkloadRole.RATE_LIMIT,
                        RedisWorkloadRole.REPLAY,
                        RedisWorkloadRole.QUEUE,
                        RedisWorkloadRole.JUDGE,
                        RedisWorkloadRole.PUBSUB);
    }
}
