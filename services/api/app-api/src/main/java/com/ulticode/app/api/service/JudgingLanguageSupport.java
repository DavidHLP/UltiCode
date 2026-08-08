package com.ulticode.app.api.service;

import java.util.Set;

/**
 * Public port for the judging module's language catalog.
 *
 * <p>Cross-module callers (problem projection, sandbox adapter) cross this
 * port instead of the submission module's internal constants.
 */
public interface JudgingLanguageSupport {

    /**
     * Languages the codebase advertises in the API (broader than what the
     * harness actually executes).
     */
    Set<String> advertisedLanguages();

    /**
     * Languages the D-form harness actually executes.
     */
    Set<String> executableLanguages();

    /**
     * Languages the runner supports (alias for {@link #executableLanguages}).
     */
    Set<String> supportedLanguages();
}
