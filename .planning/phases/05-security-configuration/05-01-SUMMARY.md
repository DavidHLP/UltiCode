---
phase: "05-security-configuration"
plan: "01"
subsystem: "security"
tags: [cors, configuration, security, externalization]
dependency_graph:
  requires: []
  provides: [cors-properties-bean]
  affects: [security-config, websocket-config]
tech_stack:
  added: []
  patterns: ["@ConfigurationProperties", "constructor-injection", "environment-variable-binding"]
key_files:
  created:
    - backend-spring/src/main/java/com/ulticode/common/config/CorsProperties.java
  modified:
    - backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java
    - backend-spring/src/main/java/com/ulticode/modules/websocket/config/WebSocketProperties.java
    - backend-spring/src/main/resources/application.yml
    - backend-spring/src/main/resources/application-dev.yml
    - backend-spring/src/main/resources/application-prod.yml
    - .env.example
decisions: []
metrics:
  duration: "2m"
  completed: "2026-04-16T20:44:55Z"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 7
---

# Phase 05 Plan 01: Externalize CORS Origins Summary

Externalized CORS allowed origins from hardcoded Java values in SecurityConfig and WebSocketProperties to environment variable-driven configuration using Spring Boot `@ConfigurationProperties`.

## What Changed

**CorsProperties.java (new):** Created a `@ConfigurationProperties(prefix = "cors")` class that binds `cors.allowed-origins` from YAML/environment. Includes a `toConfigurationSource()` method that builds the full `CorsConfigurationSource` with credentials, methods, headers, and exposed headers configuration.

**SecurityConfig.java (modified):** Replaced the inline `corsConfigurationSource()` bean that hardcoded four origin patterns with a one-liner delegating to `corsProperties.toConfigurationSource()`. Removed unused `CorsConfiguration` and `UrlBasedCorsConfigurationSource` imports.

**WebSocketProperties.java (modified):** Added `@Value("${cors.allowed-origins:...}")` to the `allowedOrigins` field so WebSocket endpoints share the same CORS origin list as HTTP CORS, bound via the same `cors.allowed-origins` property.

**YAML configs (modified):**
- `application.yml`: Added `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:9002,http://localhost:9003}`
- `application-dev.yml`: Added dev-specific origins including `127.0.0.1` variants
- `application-prod.yml`: Added `${CORS_ALLOWED_ORIGINS:...}` binding (no insecure fallback beyond defaults)

**.env.example (modified):** Added `CORS_ALLOWED_ORIGINS` documentation with Chinese description, inserted between JWT and Redis sections.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | `0b896a04f` | feat(05-01): create CorsProperties and wire into SecurityConfig and WebSocketProperties |
| 2 | `65ea3f1de` | feat(05-01): add cors.allowed-origins property to YAML configs and .env.example |

## Deviations from Plan

None - plan executed exactly as written.

## Threat Surface Scan

No new security-relevant surface introduced beyond what was in the plan's threat model. The changes reduce attack surface by removing hardcoded origins and enabling per-environment CORS configuration.

## Self-Check: PASSED
