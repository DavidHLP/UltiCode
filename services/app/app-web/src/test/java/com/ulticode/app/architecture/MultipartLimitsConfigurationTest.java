package com.ulticode.app.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartLimitsConfigurationTest {

    @Test
    void appWebAdvertisesAvatarMultipartHeadroom() throws IOException {
        Path application = repositoryRoot().resolve(
                "services/app/app-web/src/main/resources/application.yml");
        String yaml = Files.readString(application);

        assertThat(yaml).contains(
                "  servlet:\n"
                        + "    multipart:\n"
                        + "      max-file-size: 6MB\n"
                        + "      max-request-size: 6MB");
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
