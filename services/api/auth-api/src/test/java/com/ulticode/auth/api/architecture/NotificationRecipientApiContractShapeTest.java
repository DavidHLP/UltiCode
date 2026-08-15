package com.ulticode.auth.api.architecture;

import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.auth.api.service.NotificationRecipientQueryService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract-shape guard for the focused Auth recipient seam.
 */
class NotificationRecipientApiContractShapeTest {

    @Test
    void recipientQueryContractIsPresentAndEntityFree() {
        Method method = Arrays.stream(NotificationRecipientQueryService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("findRecipients"))
                .findFirst()
                .orElseThrow();

        assertThat(method.getReturnType().getName())
                .isEqualTo("com.ulticode.common.rpc.RpcResult");
        assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getName).toList())
                .containsExactly("java.util.Set");
        assertThat(Arrays.stream(AuthNotificationRecipientDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("accountId", "email", "active", "banned");
    }
}
