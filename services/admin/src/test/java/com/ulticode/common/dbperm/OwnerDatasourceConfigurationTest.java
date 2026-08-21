package com.ulticode.common.dbperm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runtime datasource is an Owner seam: a missing Owner variable must fail
 * closed instead of silently reconnecting the process to the bootstrap schema.
 */
class OwnerDatasourceConfigurationTest {

    private static final Map<String, String> OWNER_CONFIGS = Map.of(
            "AUTH", "services/auth/src/main/resources/application.yml",
            "ADMIN", "services/admin/src/main/resources/application.yml",
            "APP", "services/app/app-web/src/main/resources/application.yml",
            "NOTIFICATION", "services/notification/src/main/resources/application.yml",
            "SUBMISSION", "services/submission/src/main/resources/application.yml");

    @Test
    @DisplayName("Owner runtime datasources reject generic DB fallback")
    void ownerRuntimeDatasourcesRequireOwnerSpecificVariables() throws IOException {
        for (Map.Entry<String, String> entry : OWNER_CONFIGS.entrySet()) {
            String owner = entry.getKey();
            String yaml = Files.readString(resolveFile(entry.getValue()).toPath());

            assertThat(yaml)
                    .as("%s runtime config", owner)
                    .doesNotContain("${DB_HOST:")
                    .doesNotContain("${DB_PORT:")
                    .doesNotContain("${DB_NAME:")
                    .doesNotContain("${DB_USER:")
                    .contains("${" + owner + "_DB_HOST}")
                    .contains("${" + owner + "_DB_PORT}")
                    .contains("${" + owner + "_DB_NAME}")
                    .contains("${" + owner + "_DB_USER}")
                    .contains("${" + owner + "_DB_PASSWORD}");
        }
    }

    private static File resolveFile(String path) {
        File[] candidates = {
                new File(path),
                new File("../" + path),
                new File("../../" + path)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate;
            }
        }
        return candidates[0];
    }
}
