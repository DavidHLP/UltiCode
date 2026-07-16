package com.ulticode.modules.submission.port;

import java.util.Set;

/**
 * Public port for the judging module's language catalog (architecture-review
 * candidate #1 deepening).
 *
 * <p>Before the deepening, callers outside the {@code submission} module
 * ({@code DefaultProblemProjection}, {@code InMemorySandboxAdapter}) imported
 * {@code com.ulticode.modules.submission.service.CodeExecutionHelper} solely
 * to read the {@code SUPPORTED_LANGUAGES} / {@code DFORM_SUPPORTED_LANGUAGES}
 * constants — a DTO-internal detail leaking through the package boundary.
 *
 * <p>After the deepening, the judging module exposes only this narrow port:
 * "is this language advertised?", "is it executable by the runner?",
 * "what languages does the runner support?". The wire-shape constants and
 * the harness payload helpers stay package-private to the submission module
 * and never cross the boundary.
 *
 * <p>Cross-module callers (problem projection, sandbox adapter) cross this
 * port instead of the helper, so the judging module owns its own private
 * shape.
 *
 * @author ulticode
 */
public interface JudgingLanguageSupport {

    /**
     * Languages the codebase advertises in the API (broader than what the
     * harness actually executes — JavaScript is advertised but the D-form
     * runner does not currently support it).
     */
    Set<String> advertisedLanguages();

    /**
     * Languages the D-form harness actually executes. Smaller than
     * {@link #advertisedLanguages()}.
     */
    Set<String> executableLanguages();

    /**
     * Whether {@code language} is in {@link #advertisedLanguages()}.
     */
    boolean isAdvertised(String language);

    /**
     * Whether {@code language} is in {@link #executableLanguages()}. Used by
     * the sandbox executor and the problem projection to gate which
     * problems can run a /run request.
     */
    boolean isExecutable(String language);
}