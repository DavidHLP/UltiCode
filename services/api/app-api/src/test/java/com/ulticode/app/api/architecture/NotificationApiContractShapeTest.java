package com.ulticode.app.api.architecture;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.app.api.service.NotificationAdministrationService;
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

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }
}
