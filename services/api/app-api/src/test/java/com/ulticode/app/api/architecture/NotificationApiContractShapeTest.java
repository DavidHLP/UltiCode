package com.ulticode.app.api.architecture;

import com.ulticode.app.api.dto.NotificationRecipientDTO;
import com.ulticode.app.api.service.UserNotificationReadPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the explicit App-provided recipient/fact exception. */
class NotificationApiContractShapeTest {

    @Test
    void app_fact_seams_remain_entity_free_and_app_owned() {
        assertThat(UserNotificationReadPort.class.getPackageName())
                .isEqualTo("com.ulticode.app.api.service");
        for (Method method : UserNotificationReadPort.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName())
                    .doesNotStartWith("com.ulticode.modules.");
            assertThat(Arrays.stream(method.getParameterTypes())
                    .map(Class::getName).toList())
                    .noneMatch(type -> type.startsWith("com.ulticode.modules."));
        }
    }

    @Test
    void recipient_projection_keeps_the_minimum_fact_shape() {
        assertThat(Arrays.stream(NotificationRecipientDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("userId", "email", "active", "banned");
        assertThat(NotificationRecipientDTO.class.getPackageName())
                .isEqualTo("com.ulticode.app.api.dto");
    }
}
