package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JavaScript language profile (ADR-002 §2.2). M2a stub — the D-form
 * harness for JavaScript is not yet part of the migration; this
 * test pins the structural integrity of the stub.
 */
@DisplayName("JavaScriptLanguageProfile (ADR-002 §2.2, M2a stub)")
class JavaScriptLanguageProfileTest {

    /**
     * Direct construction (no Spring) — ConditionalOnProperty
     * disables the bean in tests by default, so we instantiate
     * manually to assert the stub behavior.
     */
    private final JavaScriptLanguageProfile profile = new JavaScriptLanguageProfile();

    @Test
    @DisplayName("languageId is 'javascript'")
    void languageId_javascript() {
        assertThat(profile.languageId()).isEqualTo("javascript");
    }

    @Test
    @DisplayName("dockerCommand throws UnsupportedLanguageException (M2a stub)")
    void dockerCommand_throwsUnsupported() {
        assertThatThrownBy(() ->
                profile.dockerCommand(
                        new SandboxJob("r", "u", "s", 0L, "javascript", "x", 2, 256),
                        Path.of("/tmp/job")))
                .isInstanceOf(UnsupportedLanguageException.class)
                .hasMessageContaining("javascript");
    }
}
