package com.ulticode.notification.api.architecture;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.notification.api.command.CreateNotificationCommand;
import com.ulticode.notification.api.command.DeleteNotificationCommand;
import com.ulticode.notification.api.command.UpdateNotificationCommand;
import com.ulticode.notification.api.dto.BadgeEarnedPayload;
import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.notification.api.dto.NotificationAdminViewDTO;
import com.ulticode.notification.api.dto.NotificationPayload;
import com.ulticode.notification.api.event.NotificationIntentEventContract;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.notification.api.service.NotificationAdministrationService;
import com.ulticode.notification.api.service.NotificationServiceContract;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationApiContractShapeTest {

    private static final List<Class<?>> CONTRACTS = List.of(
            NotificationAdminReadPort.class,
            NotificationAdministrationService.class);

    @Test
    void notificationContractsAreInTheProviderOwnedNamespace() {
        assertThat(CONTRACTS)
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .startsWith("com.ulticode.notification.api"));
        assertThat(List.of(
                CreateNotificationCommand.class,
                DeleteNotificationCommand.class,
                UpdateNotificationCommand.class,
                BadgeEarnedPayload.class,
                NotificationAdminDTO.class,
                NotificationAdminViewDTO.class,
                NotificationPayload.class,
                NotificationIntentEventContract.class))
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .startsWith("com.ulticode.notification.api"));
    }

    @Test
    void notificationCommandsCarryCrossOwnerMetadata() {
        ActorDelegation actor = new ActorDelegation(
                "ADMIN", "admin-1", "admin-1", "contract-test");
        CreateNotificationCommand create = new CreateNotificationCommand(
                "command-1", IdMetadata.mint(), actor,
                new TraceMetadata("trace-1", null, null, null),
                "creator-1", "title", "body", "SYSTEM", "SYSTEM", "ALL",
                List.<String>of());
        DeleteNotificationCommand delete = new DeleteNotificationCommand(
                "command-2", IdMetadata.mint(), actor,
                new TraceMetadata("trace-2", null, null, null), "notification-1");
        UpdateNotificationCommand update = new UpdateNotificationCommand(
                "command-3", IdMetadata.mint(), actor,
                new TraceMetadata("trace-3", null, null, null),
                "notification-1", "title", "body", "SYSTEM", "SYSTEM");

        assertThat(create.commandId()).isEqualTo("command-1");
        assertThat(delete.notificationId()).isEqualTo("notification-1");
        assertThat(update.notificationId()).isEqualTo("notification-1");
    }

    @Test
    void serviceMethodsDoNotExposeImplementationTypes() {
        for (Class<?> contract : CONTRACTS) {
            for (Method method : contract.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName())
                        .as("return type of %s#%s", contract.getName(), method.getName())
                        .doesNotStartWith("com.ulticode.modules.");
                assertThat(Arrays.stream(method.getParameterTypes())
                        .map(Class::getName).toList())
                        .as("parameter types of %s#%s", contract.getName(), method.getName())
                        .noneMatch(type -> type.startsWith("com.ulticode.modules."));
            }
        }
    }

    @Test
    void notificationServiceAndIntentWireIdentitiesArePinned() {
        assertThat(NotificationServiceContract.DUBBO_GROUP)
                .isEqualTo("backend-notification");
        assertThat(NotificationServiceContract.DUBBO_VERSION)
                .isEqualTo("1.0.0");
        assertThat(NotificationIntentEventContract.EVENT_TYPE)
                .isEqualTo("NotificationIntentCreated");
        assertThat(NotificationIntentEventContract.SCHEMA_VERSION).isEqualTo(1);
        assertThat(NotificationIntentEventContract.OWNER).isEqualTo("App");
        assertThat(NotificationIntentEventContract.INTENT_ID).isEqualTo("intentId");
        assertThat(NotificationIntentEventContract.USER_ID).isEqualTo("userId");
    }
}
