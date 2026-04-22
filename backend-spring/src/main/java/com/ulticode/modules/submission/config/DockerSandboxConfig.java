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
        Map<String, LanguageLimit> languages
) {
    public record LanguageLimit(int timeoutSeconds, String memory) {}
}
