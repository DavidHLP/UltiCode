package com.ulticode.modules.submission.codec;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * i18n cross-stack coverage test (ADR-001 Section 2.5, M1b).
 * <p>
 * Pins the invariant that every SubmissionStatus enum constant has
 * a translation key in BOTH frontends (console + management) and BOTH
 * locales (en-US + zh-CN). Any future enum addition that misses i18n
 * coverage breaks this test loudly with a precise diff (which enum, which
 * locale, which frontend), instead of silently rendering as the raw
 * Java identifier in the UI.
 * <p>
 * <b>Why scan the .ts source instead of going through a build script?</b>
 * Per ADR-001 Section 2.5 the original idea was a build-time JSON dump.
 * M1b deliberately keeps this as a JUnit test in backend-spring that
 * reads the .ts files directly: zero new frontend toolchain dependency,
 * runs with {@code ./mvnw test} like any other test, and CI sees the
 * same failure path the developer sees.
 * <p>
 * <b>How the two key formats are derived:</b>
 * <ul>
 *   <li>console (camelCase): {@code submission.status.<key>} where
 *       {@code key} is {@code SNAKE_CASE_NAME} converted to lowerCamelCase
 *       (split on underscore, lowercase the first segment, capitalize
 *       the rest, join with no separator). E.g. {@code WRONG_ANSWER}
 *       maps to {@code wrongAnswer}. Verified by
 *       {@code console/src/i18n/locales/<en-US or zh-CN>/submission.ts}
 *       status block.</li>
 *   <li>management (SCREAMING_SNAKE): {@code submission.statusLabels.<KEY>}
 *       where {@code KEY} is the enum constant name verbatim. Verified by
 *       {@code management/src/i18n/locales/<en-US or zh-CN>/modules/submissions.ts}
 *       statusLabels block.</li>
 * </ul>
 * <p>
 * Skipped (with WARN log) if the test cannot locate a repo root that
 * contains both {@code console/} and {@code management/} -- e.g. when
 * running on a backend-only checkout. This keeps the test safe for CI
 * variants that don't ship the full repo.
 */
@DisplayName("SubmissionStatus i18n cross-stack coverage (M1b)")
class SubmissionStatusI18nCoverageTest {

    private static final String CONSOLE_KEY_STYLE = "console_camelCase";
    private static final String MANAGEMENT_KEY_STYLE = "management_screaming_snake";

    @Test
    @DisplayName("test fixture sanity -- every SubmissionStatus has a unique enum name")
    void enumNamesAreUnique() {
        Set<String> names = new TreeSet<>();
        for (SubmissionStatus s : SubmissionStatus.values()) {
            assertThat(names.add(s.name()))
                    .as("Duplicate enum name: %s", s.name())
                    .isTrue();
        }
        assertThat(names).hasSize(SubmissionStatus.values().length);
    }

    @TestFactory
    @DisplayName("each SubmissionStatus is translated in every (frontend, locale) pair")
    Stream<DynamicTest> everyStatusTranslatedEverywhere() throws IOException {
        Path repoRoot = findRepoRoot();
        if (repoRoot == null) {
            return Stream.of(DynamicTest.dynamicTest(
                    "SKIPPED -- repo root with console/ + management/ not found",
                    () -> {
                        // Visibility for CI log; intentionally no assertion.
                        System.err.println(
                                "[M1b WARN] repo root with console/ + management/ not found; "
                                        + "SubmissionStatusI18nCoverageTest skipped. "
                                        + "Set ULTICODE_ROOT or run from a full checkout.");
                    }));
        }

        Map<LocaleFile, Set<String>> keysByFile = loadAllKeySets(repoRoot);

        return Stream.of(LocaleFile.values())
                .flatMap(lf -> {
                    // Derive key style from the file: console files hold
                    // lowerCamelCase keys, management files hold SCREAMING_SNAKE.
                    String style = lf.shortName().startsWith("console")
                            ? CONSOLE_KEY_STYLE
                            : MANAGEMENT_KEY_STYLE;
                    return Stream.of(SubmissionStatus.values())
                            .map(status -> DynamicTest.dynamicTest(
                                    lf.shortName() + " / " + style + " / " + status.name(),
                                    () -> assertThat(keysByFile.get(lf))
                                            .as("missing translation key '%s' in %s (%s) for %s",
                                                    expectedKey(status, style),
                                                    lf.path().getFileName(),
                                                    style,
                                                    status.name())
                                            .contains(expectedKey(status, style))
                            ));
                });
    }

    @Test
    @DisplayName("i18n key counts match enum count in every (frontend, locale) pair")
    void noMissingNorExtraKeys() throws IOException {
        Path repoRoot = findRepoRoot();
        if (repoRoot == null) {
            return; // skip silently; the dynamic-test above already logs
        }
        Map<LocaleFile, Set<String>> keysByFile = loadAllKeySets(repoRoot);

        for (LocaleFile lf : LocaleFile.values()) {
            // Each locale file is intrinsically one key style: console
            // uses lowerCamelCase, management uses SCREAMING_SNAKE. We
            // derive the expected style from the file, not from a second
            // loop -- otherwise we would assert that a console file
            // contains SCREAMING_SNAKE keys (impossible) and vice versa.
            String style = lf.shortName().startsWith("console")
                    ? CONSOLE_KEY_STYLE
                    : MANAGEMENT_KEY_STYLE;
            Set<String> expected = new TreeSet<>();
            for (SubmissionStatus s : SubmissionStatus.values()) {
                expected.add(expectedKey(s, style));
            }
            Set<String> actual = keysByFile.get(lf);

            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new TreeSet<>(actual);
            extra.removeAll(expected);

            assertThat(missing)
                    .as("missing translation keys in %s (%s); add these keys: %s",
                            lf.path().getFileName(), style, missing)
                    .isEmpty();
            assertThat(extra)
                    .as("orphan translation keys in %s (%s); "
                            + "either re-add the corresponding SubmissionStatus enum constant "
                            + "or remove these keys: %s",
                            lf.path().getFileName(), style, extra)
                    .isEmpty();
        }
    }

    // --- key derivation -------------------------------------------------

    /**
     * Compute the i18n key for a given enum constant under the given
     * key style. Public for test introspection / debugging.
     */
    static String expectedKey(SubmissionStatus status, String keyStyle) {
        if (CONSOLE_KEY_STYLE.equals(keyStyle)) {
            return toLowerCamel(status.name());
        }
        if (MANAGEMENT_KEY_STYLE.equals(keyStyle)) {
            return status.name(); // SCREAMING_SNAKE verbatim
        }
        throw new IllegalArgumentException("Unknown key style: " + keyStyle);
    }

    /**
     * Convert {@code SNAKE_CASE_NAME} to {@code snakeCaseName}. Handles
     * single-word names ({@code PENDING} maps to {@code pending}) and
     * multi-word ({@code WRONG_ANSWER} maps to {@code wrongAnswer},
     * {@code TIME_LIMIT_EXCEEDED} maps to {@code timeLimitExceeded}).
     */
    static String toLowerCamel(String snake) {
        String[] parts = snake.toLowerCase().split("_");
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
        }
        return out.toString();
    }

    // --- file loading ---------------------------------------------------

    private enum LocaleFile {
        CONSOLE_EN("console/src/i18n/locales/en-US/submission.ts", "console/en"),
        CONSOLE_ZH("console/src/i18n/locales/zh-CN/submission.ts", "console/zh"),
        MGMT_EN("management/src/i18n/locales/en-US/modules/submissions.ts", "mgmt/en"),
        MGMT_ZH("management/src/i18n/locales/zh-CN/modules/submissions.ts", "mgmt/zh");

        private final Path relativePath;
        private final String shortName;

        LocaleFile(String relativePath, String shortName) {
            this.relativePath = Path.of(relativePath);
            this.shortName = shortName;
        }

        Path path() { return relativePath; }
        String shortName() { return shortName; }
    }

    /**
     * Walk up from {@code user.dir} looking for a directory that contains
     * both {@code console/} and {@code management/}. Returns null if no
     * such root is found within 4 levels.
     */
    private static Path findRepoRoot() {
        String envRoot = System.getenv("ULTICODE_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            Path env = Path.of(envRoot);
            if (isRepoRoot(env)) {
                return env.toAbsolutePath().normalize();
            }
        }
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            if (isRepoRoot(dir)) {
                return dir;
            }
            Path parent = dir.getParent();
            if (parent == null) {
                return null;
            }
            dir = parent;
        }
        return null;
    }

    private static boolean isRepoRoot(Path dir) {
        return Files.isDirectory(dir.resolve("console"))
                && Files.isDirectory(dir.resolve("management"));
    }

    /**
     * Read every locale file, extract the relevant key set, return by file.
     * Console files use the {@code status: {...}} block; management files
     * use the {@code statusLabels: {...}} block. Both blocks are detected
     * by leading whitespace + the block opener; the block body is scanned
     * line by line for {@code <key>:} or {@code <KEY>:} entries.
     */
    private static Map<LocaleFile, Set<String>> loadAllKeySets(Path repoRoot) throws IOException {
        Map<LocaleFile, Set<String>> result = new LinkedHashMap<>();
        for (LocaleFile lf : LocaleFile.values()) {
            Path file = repoRoot.resolve(lf.path());
            result.put(lf, extractKeys(file, lf.shortName().startsWith("console")));
        }
        return result;
    }

    private static Set<String> extractKeys(Path file, boolean consoleStyle) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String blockMarker = consoleStyle ? "status: {" : "statusLabels: {";
        Pattern keyPattern = consoleStyle
                ? Pattern.compile("^\\s*([a-z][a-zA-Z0-9]*)\\s*:")
                : Pattern.compile("^\\s*([A-Z_][A-Z0-9_]*)\\s*:");

        Set<String> keys = new TreeSet<>();
        boolean inBlock = false;
        int braceDepth = 0;
        for (String raw : lines) {
            String line = raw.replace("\t", "    ");
            if (!inBlock) {
                if (line.contains(blockMarker)) {
                    inBlock = true;
                    braceDepth = 1;
                    int markerIdx = line.indexOf(blockMarker) + blockMarker.length();
                    String rest = line.substring(markerIdx);
                    for (char c : rest.toCharArray()) {
                        if (c == '{') braceDepth++;
                        else if (c == '}') braceDepth--;
                    }
                    if (braceDepth == 0) break; // single-line block (rare)
                }
                continue;
            }
            // Already in block. Count braces to find end; collect keys in between.
            for (char c : line.toCharArray()) {
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
            }
            if (braceDepth <= 0) {
                break;
            }
            Matcher m = keyPattern.matcher(line);
            if (m.find()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }
}
