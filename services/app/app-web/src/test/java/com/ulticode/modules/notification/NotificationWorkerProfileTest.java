package com.ulticode.modules.notification;

import com.ulticode.modules.achievement.consumer.SubmissionJudgedAchievementConsumer;
import com.ulticode.modules.contest.consumer.SubmissionJudgedContestConsumer;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.SubmissionJudgedInboxBridge;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import com.ulticode.modules.notification.ledger.reaper.NotificationLedgerReaper;
import com.ulticode.modules.websocket.consumer.SubmissionJudgedWebSocketConsumer;
import com.ulticode.common.uuid.UuidGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
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

/**
 * NOTIFY-004: the Notification Delivery worker runtime-role gate.
 *
 * <p>Verifies the {@code @ConditionalOnProperty} seam that separates the
 * delivery worker from the HTTP/API role without booting the full Spring
 * context: the durable inbox bridge and ledger reaper are registered only
 * while {@code ulticode.notification.worker.enabled} is true (or missing),
 * and are absent when the property is false (the {@code api} profile default).
 */
@DisplayName("NOTIFY-004: Notification Delivery worker profile gate")
class NotificationWorkerProfileTest {

    @Configuration
    static class WorkerDependencies {
        @Bean
        StringRedisTemplate redisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ConsumerInboxMapper inboxMapper() {
            return mock(ConsumerInboxMapper.class);
        }

        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        @Bean
        UuidGenerator uuidGenerator() {
            return () -> UUID.randomUUID().toString();
        }

        @Bean
        SubmissionJudgedNotificationConsumer notificationConsumer() {
            return mock(SubmissionJudgedNotificationConsumer.class);
        }

        @Bean
        NotificationIntentEventConsumer notificationIntentConsumer() {
            return mock(NotificationIntentEventConsumer.class);
        }

        @Bean
        SubmissionJudgedAchievementConsumer achievementConsumer() {
            return mock(SubmissionJudgedAchievementConsumer.class);
        }

        @Bean
        SubmissionJudgedWebSocketConsumer webSocketConsumer() {
            return mock(SubmissionJudgedWebSocketConsumer.class);
        }

        @Bean
        SubmissionJudgedContestConsumer contestConsumer() {
            return mock(SubmissionJudgedContestConsumer.class);
        }

        @Bean
        NotificationDeliveryLedgerMapper ledgerMapper() {
            return mock(NotificationDeliveryLedgerMapper.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return mock(MeterRegistry.class);
        }
    }

    private static AnnotationConfigApplicationContext context(boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (enabled) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("worker-gate", Map.of(
                            "ulticode.notification.worker.enabled", "true")));
        } else {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("worker-gate", Map.of(
                            "ulticode.notification.worker.enabled", "false")));
        }
        context.register(WorkerDependencies.class);
        context.scan(
                "com.ulticode.modules.event.inbox",
                "com.ulticode.modules.notification.ledger.reaper");
        context.refresh();
        return context;
    }

    @Test
    @DisplayName("enabled=true registers the delivery worker beans")
    void enabledRegistersDeliveryWorker() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertThat(context.getBeanProvider(SubmissionJudgedInboxBridge.class).getIfAvailable())
                    .isNotNull();
            assertThat(context.getBeanProvider(NotificationLedgerReaper.class).getIfAvailable())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("missing property keeps the legacy combined-profile default (worker on)")
    void missingPropertyDefaultsToWorkerOn() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(WorkerDependencies.class);
            context.scan(
                    "com.ulticode.modules.event.inbox",
                    "com.ulticode.modules.notification.ledger.reaper");
            context.refresh();
            assertThat(context.getBeanProvider(SubmissionJudgedInboxBridge.class).getIfAvailable())
                    .isNotNull();
            assertThat(context.getBeanProvider(NotificationLedgerReaper.class).getIfAvailable())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("enabled=false (api profile) does not register the delivery worker beans")
    void disabledExcludesDeliveryWorker() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertThat(context.getBeanProvider(SubmissionJudgedInboxBridge.class).getIfAvailable())
                    .isNull();
            assertThat(context.getBeanProvider(NotificationLedgerReaper.class).getIfAvailable())
                    .isNull();
        }
    }
}
