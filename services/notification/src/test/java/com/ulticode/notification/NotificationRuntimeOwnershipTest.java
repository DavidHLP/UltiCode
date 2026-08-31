package com.ulticode.notification;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.ulticode.notification.idempotency.entity.NotificationCommandReceiptEntity;
import com.ulticode.websecurity.jwt.RedisDelegationAssertionReplayGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRuntimeOwnershipTest {

    @Test
    void bootScanContainsOnlyNotificationOwnedRuntimePackages() {
        SpringBootApplication application = BackendNotificationApplication.class
                .getAnnotation(SpringBootApplication.class);

        assertThat(application.scanBasePackages())
                .containsExactlyInAnyOrder(
                        "com.ulticode.notification",
                        "com.ulticode.modules.notification",
                        "com.ulticode.modules.email");
    }

    @Test
    void bootImportsSharedDelegationReplayGuard() {
        Import sharedImport = BackendNotificationApplication.class.getAnnotation(Import.class);

        assertThat(sharedImport.value())
                .containsExactly(RedisDelegationAssertionReplayGuard.class);
    }

    @Test
    void runtimeDoesNotDependOnAppDomainOrWebSocketImplementations() {
        JavaClasses classes = new ClassFileImporter().importPackages(
                "com.ulticode.notification",
                "com.ulticode.modules.notification",
                "com.ulticode.modules.email");

        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(
                        "com.ulticode.notification..",
                        "com.ulticode.modules.notification..",
                        "com.ulticode.modules.email..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ulticode.app.config..",
                        "com.ulticode.app.security..",
                        "com.ulticode.modules.achievement..",
                        "com.ulticode.modules.contest..",
                        "com.ulticode.modules.problem..",
                        "com.ulticode.modules.submission..",
                        "com.ulticode.modules.websocket..")
                .check(classes);
    }

    @Test
    void commandReceiptUsesNotificationOwnedTable() {
        assertThat(NotificationCommandReceiptEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("notification_command_receipt");
    }
}
