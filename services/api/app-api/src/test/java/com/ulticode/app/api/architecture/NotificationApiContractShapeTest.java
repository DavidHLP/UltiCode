package com.ulticode.app.api.architecture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that {@code UserNotificationReadPort} and
 * {@code NotificationRecipientDTO} have been relocated from app-api to the
 * Notification owner module. They were misclassified as App-owned contracts
 * although only Notification consumes and provides them.
 */
class NotificationApiContractShapeTest {

    @Test
    void userNotificationReadPortIsNotDeclaredInAppApi() {
        assertThat(getClassByName("com.ulticode.app.api.service.UserNotificationReadPort"))
                .as("UserNotificationReadPort must not exist in app-api")
                .isNull();
    }

    @Test
    void notificationRecipientDtoIsNotDeclaredInAppApi() {
        assertThat(getClassByName("com.ulticode.app.api.dto.NotificationRecipientDTO"))
                .as("NotificationRecipientDTO must not exist in app-api")
                .isNull();
    }

    private static Class<?> getClassByName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
