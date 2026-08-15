package com.ulticode.notification.dubbo.provider;

import com.ulticode.app.api.service.NotificationServiceContract;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the notification provider identity before the implementation moves
 * out of the App boot shell.
 */
class NotificationProviderContractTest {

    @Test
    void notificationProvidersUseTheTargetOwnerGroup() {
        assertTargetGroup(NotificationAdminReadProvider.class);
        assertTargetGroup(NotificationAdministrationProvider.class);
    }

    private static void assertTargetGroup(Class<?> providerType) {
        DubboService annotation = providerType.getAnnotation(DubboService.class);
        assertThat(annotation)
                .as("Dubbo provider annotation on %s", providerType.getName())
                .isNotNull();
        assertThat(annotation.group()).isEqualTo(NotificationServiceContract.DUBBO_GROUP);
        assertThat(annotation.version()).isEqualTo(NotificationServiceContract.DUBBO_VERSION);
    }
}
