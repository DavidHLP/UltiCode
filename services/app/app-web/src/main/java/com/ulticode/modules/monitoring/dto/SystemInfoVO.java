package com.ulticode.modules.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * System information VO for monitoring endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfoVO {
    /**
     * System uptime in seconds.
     */
    private Long uptime;

    /**
     * Java runtime version.
     */
    private String javaVersion;

    /**
     * Operating system name.
     */
    private String platform;

    /**
     * Server hostname.
     */
    private String hostname;

    /**
     * Application environment (development, staging, production).
     */
    private String env;

    /**
     * Process ID.
     */
    private Long pid;

    /**
     * Application version.
     */
    private String version;
}
