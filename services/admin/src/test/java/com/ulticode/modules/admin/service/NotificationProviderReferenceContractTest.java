package com.ulticode.modules.admin.service;

import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.app.api.service.NotificationServiceContract;
import com.ulticode.modules.admin.port.adapter.DubboNotificationAdminReadAdapter;
import com.ulticode.modules.admin.service.impl.AdminNotificationServiceImpl;
import org.apache.dubbo.config.annotation.DubboReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures every Admin notification consumer resolves the same target owner.
 */
class NotificationProviderReferenceContractTest {

    @Test
    void adminNotificationConsumersUseTheTargetOwnerGroup() throws Exception {
        assertReferenceGroup(
                DubboNotificationAdminReadAdapter.class,
                "notificationAdminReadPort",
                NotificationAdminReadPort.class);
        assertReferenceGroup(
                AdminNotificationServiceImpl.class,
                "dubboProvider",
                NotificationAdministrationService.class);
        assertReferenceGroup(
                NotificationCutoverService.class,
                "dubboProvider",
                NotificationAdministrationService.class);
    }

    private static void assertReferenceGroup(
            Class<?> owner, String fieldName, Class<?> expectedType) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        assertThat(field.getType()).isEqualTo(expectedType);
        DubboReference reference = field.getAnnotation(DubboReference.class);
        assertThat(reference).isNotNull();
        assertThat(reference.group()).isEqualTo(NotificationServiceContract.DUBBO_GROUP);
        assertThat(reference.version()).isEqualTo(NotificationServiceContract.DUBBO_VERSION);
    }
}
