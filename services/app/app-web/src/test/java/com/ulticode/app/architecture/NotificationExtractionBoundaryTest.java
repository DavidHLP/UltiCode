package com.ulticode.app.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationExtractionBoundaryTest {

    @Test
    void appRetainsOnlyNotificationEventPublishingAndLegacyPushSeams() throws IOException {
        Path root = repositoryRoot();
        Path appWeb = root.resolve("services/app/app-web");
        Path source = appWeb.resolve("src/main/java");

        assertNoRegularFiles(appWeb.resolve("src/main/java/com/ulticode/modules/email"));
        for (String movedPath : List.of(
                "com/ulticode/modules/notification/controller",
                "com/ulticode/modules/notification/dispatcher",
                "com/ulticode/modules/notification/channel",
                "com/ulticode/modules/notification/ledger",
                "com/ulticode/modules/notification/mapper",
                "com/ulticode/modules/notification/service",
                "com/ulticode/modules/notification/dto",
                "com/ulticode/modules/notification/consumer")) {
            assertNoRegularFiles(source.resolve(movedPath));
        }
        for (String movedFile : List.of(
                "com/ulticode/modules/notification/entity/Notification.java",
                "com/ulticode/modules/notification/entity/NotificationPreference.java",
                "com/ulticode/modules/notification/port/UserEmailPort.java")) {
            assertThat(source.resolve(movedFile)).doesNotExist();
        }

        assertThat(source.resolve("com/ulticode/modules/notification/event/NotificationIntentEventPublisher.java"))
                .exists();
        assertThat(source.resolve("com/ulticode/modules/notification/intent"))
                .exists();

        String bridge = Files.readString(source.resolve(
                "com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java"));
        assertThat(bridge).doesNotContain("App-Notification", "NotificationIntentEventConsumer");
        assertThat(Files.readString(appWeb.resolve("src/main/resources/application.yml")))
                .doesNotContain("ulticode.notification.worker.enabled")
                .contains("APP_INBOX_ENABLED");
        assertThat(Files.readString(appWeb.resolve("pom.xml")))
                .doesNotContain("backend-notification-domain", "spring-boot-starter-mail");
        assertThat(Files.readString(appWeb.resolve("src/main/resources/application.yml")))
                .doesNotContain("spring.mail", "mail.smtp");
    }

    @Test
    void appMainSourcesDoNotImportMovedNotificationRuntime() throws IOException {
        Path source = repositoryRoot().resolve("services/app/app-web/src/main/java");
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String contents = Files.readString(path);
                            String imports = contents.lines()
                                    .filter(line -> line.startsWith("import "))
                                    .collect(Collectors.joining("\n"));
                            assertThat(imports)
                                    .as("moved notification runtime import in %s", path)
                                    .doesNotContain(
                                            "com.ulticode.modules.notification.channel",
                                            "com.ulticode.modules.notification.consumer",
                                            "com.ulticode.modules.notification.dispatcher",
                                            "com.ulticode.modules.notification.ledger",
                                            "com.ulticode.modules.notification.mapper",
                                            "com.ulticode.modules.notification.service.impl",
                                            "com.ulticode.modules.notification.dto",
                                            "com.ulticode.modules.email",
                                            "org.springframework.mail");
                        } catch (IOException e) {
                            throw new IllegalStateException("Unable to inspect " + path, e);
                        }
                    });
        }
    }

    private static void assertNoRegularFiles(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            assertThat(files.anyMatch(Files::isRegularFile))
                    .as("moved source remains under %s", path)
                    .isFalse();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("services/app/app-web/src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from "
                + System.getProperty("user.dir"));
    }
}
