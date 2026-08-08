package com.ulticode.modules.submission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "code-execution.sandbox")
public record DockerSandboxConfig(
        boolean enabled,
        String image,
        String memory,
        String cpus,
        int timeout,
        int pidsLimit,
        String seccompProfilePath,
        /**
         * D-form (LeetCode/HackerRank harness) refactor. When enabled, the
         * backend dispatches user code through the pre-compiled harness in
         * /opt/harness/{lang}/ inside the sandbox image instead of building
         * a per-request bash wrapper (the legacy Form A path). The
         * migration is staged: Phase 3 ships the dispatcher, Phase 5
         * deletes the Form A wrapper code.
         */
        DForm dform,
        Map<String, LanguageLimit> languages
) {
    public record DForm(boolean enabled, String harnessRoot) {}

    public record LanguageLimit(int timeoutSeconds, String memory) {}

    /**
     * Convenience accessor used by {@code SandboxServiceImpl} so callers
     * don't have to null-check the nested record. Returns {@code false}
     * when {@link #dform()} is null (e.g. older config without the field).
     */
    public boolean dFormEnabled() {
        return dform != null && dform.enabled();
    }

    public String dFormHarnessRoot() {
        return dform != null && dform.harnessRoot() != null ? dform.harnessRoot() : "/opt/harness";
    }
}
