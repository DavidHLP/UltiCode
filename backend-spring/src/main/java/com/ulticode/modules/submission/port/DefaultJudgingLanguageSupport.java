package com.ulticode.modules.submission.port;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Default {@link JudgingLanguageSupport} backed by the submission module's
 * own constants (architecture-review candidate #1 deepening).
 *
 * <p>This bean is the public seam for the language catalog. The wire-shape
 * constants stop crossing module boundaries directly — cross-module callers
 * go through this bean.
 *
 * @author ulticode
 */
@Component
public class DefaultJudgingLanguageSupport implements JudgingLanguageSupport {

    /**
     * Languages the codebase advertises in the API. Kept as a Set so the
     * controller can validate before reaching the dispatcher. The actual
     * set of languages whose execution paths are implemented lives in
     * {@link #EXECUTABLE}.
     */
    private static final Set<String> ADVERTISED = Set.of(
            "javascript", "python", "java", "c", "cpp"
    );

    /**
     * Languages the D-form harness actually supports. Smaller than
     * {@link #ADVERTISED} because:
     * <ul>
     *   <li>JavaScript isn't part of the migration (no JS harness yet)</li>
     *   <li>C still needs a complete harness implementation (the
     *       {@code docker/sandbox/harness/c/} tree is a Phase 1 smoke
     *       skeleton that doesn't read {@code input.json}). Re-add after
     *       an envelope-producing C harness ships.</li>
     *   <li>C++ is supported: the harness in
     *       {@code docker/sandbox/harness/cpp/} parses the Solution
     *       signature, generates a typed runner, and emits the D-form
     *       envelope. Requires {@code sandbox.profile.cpp.enabled=true}
     *       and a sandbox image built with the harness.</li>
     * </ul>
     */
    private static final Set<String> EXECUTABLE = Set.of("java", "python", "cpp");

    @Override
    public Set<String> advertisedLanguages() {
        return ADVERTISED;
    }

    @Override
    public Set<String> executableLanguages() {
        return EXECUTABLE;
    }

    @Override
    public boolean isAdvertised(String language) {
        return language != null && ADVERTISED.contains(language.toLowerCase().trim());
    }

    @Override
    public boolean isExecutable(String language) {
        return language != null && EXECUTABLE.contains(language.toLowerCase().trim());
    }
}