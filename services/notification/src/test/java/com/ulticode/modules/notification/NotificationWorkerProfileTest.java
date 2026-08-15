package com.ulticode.modules.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import com.ulticode.modules.notification.ledger.reaper.NotificationLedgerReaper;
import com.ulticode.notification.inbox.NotificationIntegrationInboxBridge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationWorkerProfileTest {

    @Configuration
    static class Dependencies {
        @Bean StringRedisTemplate redisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean ConsumerInboxMapper inboxMapper() { return mock(ConsumerInboxMapper.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean UuidGenerator uuidGenerator() { return () -> UUID.randomUUID().toString(); }
        @Bean SubmissionJudgedNotificationConsumer judgedConsumer() {
            return mock(SubmissionJudgedNotificationConsumer.class);
        }
        @Bean NotificationIntentEventConsumer intentConsumer() {
            return mock(NotificationIntentEventConsumer.class);
        }
        @Bean NotificationDeliveryLedgerMapper ledgerMapper() {
            return mock(NotificationDeliveryLedgerMapper.class);
        }
        @Bean MeterRegistry meterRegistry() { return mock(MeterRegistry.class); }
    }

    @Test
    void enabledRegistersNotificationWorkerBeans() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertThat(context.getBean(NotificationIntegrationInboxBridge.class)).isNotNull();
            assertThat(context.getBean(NotificationLedgerReaper.class)).isNotNull();
        }
    }

    @Test
    void disabledLeavesHttpOnlyRoleWithoutWorkerBeans() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertThat(context.getBeanProvider(NotificationIntegrationInboxBridge.class)
                    .getIfAvailable()).isNull();
            assertThat(context.getBeanProvider(NotificationLedgerReaper.class)
                    .getIfAvailable()).isNull();
        }
    }

    private static AnnotationConfigApplicationContext context(boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "worker-gate", Map.of("ulticode.notification.worker.enabled", Boolean.toString(enabled))));
        context.register(Dependencies.class);
        context.scan("com.ulticode.notification.inbox",
                "com.ulticode.modules.notification.ledger.reaper");
        context.refresh();
        return context;
    }
}
