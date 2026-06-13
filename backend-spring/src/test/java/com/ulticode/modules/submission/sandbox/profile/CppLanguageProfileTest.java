package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C++ language profile (ADR-002 §2.2). M2a stub — mirrors
 * {@link CLanguageProfileTest} and pins the future-ready
 * compile-failure heuristic.
 */
@DisplayName("CppLanguageProfile (ADR-002 §2.2, M2a stub)")
class CppLanguageProfileTest {

    private final CppLanguageProfile profile = new CppLanguageProfile();

    @Test
    @DisplayName("languageId is 'cpp'")
    void languageId_cpp() {
        assertThat(profile.languageId()).isEqualTo("cpp");
    }

    @Test
    @DisplayName("dockerCommand throws UnsupportedLanguageException (M2a stub)")
    void dockerCommand_throwsUnsupported() {
        assertThatThrownBy(() ->
                profile.dockerCommand(
                        new SandboxJob("r", "u", "s", 0L, "cpp", "#include <iostream>", 2, 256),
                        Path.of("/tmp/job")))
                .isInstanceOf(UnsupportedLanguageException.class)
                .hasMessageContaining("cpp");
    }

    @Test
    @DisplayName("isCompileFailure recognizes g++ error markers (still in stub)")
    void isCompileFailure_markers() {
        assertThat(profile.isCompileFailure("solution.cpp:8:5: error: 'foo' was not declared")).isTrue();
        assertThat(profile.isCompileFailure("error: expected '}' at end of input")).isTrue();
        assertThat(profile.isCompileFailure("terminate called after throwing an instance of 'std::bad_alloc'")).isFalse();
        assertThat(profile.isCompileFailure(null)).isFalse();
    }
}
