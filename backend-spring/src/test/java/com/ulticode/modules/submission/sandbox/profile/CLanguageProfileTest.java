package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C language profile (ADR-002 §2.2). M2a stub — the D-form harness
 * for C is in Phase 1 smoke skeleton and does not yet read
 * {@code input.json}.
 */
@DisplayName("CLanguageProfile (ADR-002 §2.2, M2a stub)")
class CLanguageProfileTest {

    private final CLanguageProfile profile = new CLanguageProfile();

    @Test
    @DisplayName("languageId is 'c'")
    void languageId_c() {
        assertThat(profile.languageId()).isEqualTo("c");
    }

    @Test
    @DisplayName("dockerCommand throws UnsupportedLanguageException (M2a stub)")
    void dockerCommand_throwsUnsupported() {
        assertThatThrownBy(() ->
                profile.dockerCommand(
                        new SandboxJob("r", "u", "s", 0L, "c", "#include <stdio.h>", 2, 256),
                        Path.of("/tmp/job")))
                .isInstanceOf(UnsupportedLanguageException.class)
                .hasMessageContaining("c");
    }

    @Test
    @DisplayName("isCompileFailure recognizes gcc / clang error markers (still in stub)")
    void isCompileFailure_markers() {
        // The stub keeps the future-ready heuristic so it doesn't
        // need to be re-written when the C harness ships.
        assertThat(profile.isCompileFailure("solution.c:5:1: error: expected ';' before 'return'")).isTrue();
        assertThat(profile.isCompileFailure("error: use of undeclared identifier 'foo'")).isTrue();
        assertThat(profile.isCompileFailure("Segmentation fault (core dumped)")).isFalse();
        assertThat(profile.isCompileFailure(null)).isFalse();
    }
}
