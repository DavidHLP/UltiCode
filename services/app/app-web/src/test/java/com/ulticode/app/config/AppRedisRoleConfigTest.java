package com.ulticode.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class AppRedisRoleConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisAutoConfiguration.class,
                    AppRedisRoleConfig.class));

    @Test
    void roleAliasesPreserveBootRedisConnectionFactory() {
        runner.withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RedisConnectionFactory connectionFactory =
                            context.getBean("redisConnectionFactory", RedisConnectionFactory.class);

                    assertThat(context.getBean("redisStreamsConnectionFactory"))
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisCacheConnectionFactory"))
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisRateLimitConnectionFactory"))
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisReplayConnectionFactory"))
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisQueueConnectionFactory"))
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisJudgeConnectionFactory"))
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisPubsubConnectionFactory"))
                            .isSameAs(connectionFactory);
                });
    }
}
