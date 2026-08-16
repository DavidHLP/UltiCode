package com.ulticode.modules.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SEARCH-003 AC3: the Search worker ({@code backend-search}) is the only
 * MeiliSearch index writer. This scan proves no first-party App/Auth main
 * source calls a MeiliSearch write API; reads (search / getDocuments for the
 * backfill diff) are the only allowed touch points.
 *
 * <p>Scanned roots: the App domain modules, the app-web boot shell and the
 * Auth owner service. {@code backend-search} itself is intentionally not
 * scanned — it owns the writes (DEC-011). Mirrors the design-system
 * first-party literal scanner precedent.
 */
@DisplayName("MeiliSearch write-path retirement scan")
class MeiliWritePathScanTest {

    private static final List<String> FORBIDDEN_WRITE_TOKENS = List.of(
            "addDocuments", "updateDocuments", "deleteDocument",
            "deleteAllDocuments", "createIndex", "deleteIndex");

    @Test
    void appAndAuthMainSourcesNeverCallMeiliWriteApis() throws IOException {
        List<Path> roots = List.of(
                Path.of("../modules"),
                Path.of("src/main"),
                Path.of("../../auth/src/main"));

        List<String> violations = new ArrayList<>();
        int scannedFiles = 0;
        for (Path root : roots) {
            assertThat(Files.isDirectory(root))
                    .as("scan root must exist: %s", root.toAbsolutePath())
                    .isTrue();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    scannedFiles++;
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        for (String token : FORBIDDEN_WRITE_TOKENS) {
                            if (lines.get(i).contains(token)) {
                                violations.add(file + ":" + (i + 1) + ": " + token);
                            }
                        }
                    }
                }
            }
        }

        assertThat(scannedFiles)
                .as("scan must actually traverse App/Auth main sources")
                .isGreaterThan(100);
        assertThat(violations)
                .as("MeiliSearch write calls outside backend-search (AC3)")
                .isEmpty();
    }
}
