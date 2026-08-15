package com.ulticode.app.api.architecture;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationRecipientDTO;
import com.ulticode.app.api.event.NotificationIntentEventContract;
import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.app.api.service.NotificationServiceContract;
import com.ulticode.app.api.service.UserNotificationReadPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationApiContractShapeTest {

    private static final List<Class<?>> CONTRACTS = List.of(
            NotificationAdminReadPort.class,
            NotificationAdministrationService.class,
            UserNotificationReadPort.class);

    @Test
    void notificationContractsExposeNoAppPrivateEntityOrMapperTypes() {
        for (Class<?> contract : CONTRACTS) {
            for (Method method : contract.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName())
                        .as("return type of %s#%s", contract.getName(), method.getName())
                        .doesNotStartWith("com.ulticode.modules.");
                assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getName).toList())
                        .as("parameter types of %s#%s", contract.getName(), method.getName())
                        .noneMatch(type -> type.startsWith("com.ulticode.modules."));
            }
        }
    }

    @Test
    void notificationCommandsCarryCrossOwnerMetadata() {
        assertThat(recordComponentNames(CreateNotificationCommand.class))
                .startsWith("commandId", "idempotency", "actor", "trace");
        assertThat(recordComponentNames(DeleteNotificationCommand.class))
                .startsWith("commandId", "idempotency", "actor", "trace");
        assertThat(recordComponentNames(UpdateNotificationCommand.class))
                .startsWith("commandId", "idempotency", "actor", "trace");
    }

    @Test
    void notificationServiceIdentityIsPinnedToTheTargetOwner() {
        assertThat(NotificationServiceContract.DUBBO_GROUP)
                .isEqualTo("backend-notification");
        assertThat(NotificationServiceContract.DUBBO_VERSION)
                .isEqualTo("1.0.0");
    }

    @Test
    void notificationRecipientContractRemainsMinimumAndEntityFree() {
        assertThat(Arrays.stream(NotificationRecipientDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("userId", "email", "active", "banned");
        assertThat(NotificationRecipientDTO.class.getPackageName())
                .isEqualTo("com.ulticode.app.api.dto");
    }

    @Test
    void notificationIntentWireContractPinsExistingFlatV1Schema() {
        assertThat(NotificationIntentEventContract.EVENT_TYPE)
                .isEqualTo("NotificationIntentCreated");
        assertThat(NotificationIntentEventContract.SCHEMA_VERSION)
                .isEqualTo(1);
        assertThat(NotificationIntentEventContract.INTENT_TYPE)
                .isEqualTo("intentType");
        assertThat(NotificationIntentEventContract.INTENT_ID)
                .isEqualTo("intentId");
        assertThat(NotificationIntentEventContract.USER_ID)
                .isEqualTo("userId");
        assertThat(NotificationIntentEventContract.CATEGORY)
                .isEqualTo("category");
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }
}
