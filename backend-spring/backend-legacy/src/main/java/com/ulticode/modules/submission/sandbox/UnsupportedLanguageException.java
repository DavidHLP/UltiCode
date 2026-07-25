package com.ulticode.modules.submission.sandbox;

/**
 * Thrown by {@link SandboxExecutor} when the requested language is not
 * backed by any registered {@link LanguageProfile}.
 *
 * <p>This is distinct from a per-case verdict: the port decides up
 * front whether the language is runnable, before materializing a
 * workspace. Mapping this to an HTTP 400 is the caller's job
 * (currently the global exception handler).
 *
 * <p>Note: a language that is <i>registered</i> but currently disabled
 * (e.g. via {@code @ConditionalOnProperty}) should still surface as
 * this exception — the contract from the caller's perspective is the
 * same: the language is not runnable in this deployment.
 */
public class UnsupportedLanguageException extends RuntimeException {

    private final String languageId;

    public UnsupportedLanguageException(String languageId) {
        super("Unsupported language: " + languageId);
        this.languageId = languageId;
    }

    public String languageId() {
        return languageId;
    }
}
