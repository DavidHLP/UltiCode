---
name: springboot-rules
description: Spring Boot backend conventions for the UltiCode API.
globs:
  - services/pom.xml
  - services/**/pom.xml
  - {backend-auth,backend-admin,backend-app}/pom.xml
  - services/**/src/**/*.{java,yml,yaml,properties}
  - {backend-auth,backend-admin,backend-app}/src/**/*.{java,yml,yaml,properties}
condition: ["(?i)Spring|Boot"]
interruptMode: never
alwaysApply: false
---

# Spring Boot rules

- Use constructor injection. Do not introduce field injection, circular dependencies, or `@Lazy` solely to hide a dependency cycle.
- Controllers **MUST** bind typed request models, trigger boundary validation, delegate business behavior, and return the established response contract.
- Spring proxy annotations such as `@Transactional`, `@Async`, `@Cacheable`, and method security **MUST** be placed on methods reached through the proxy. Do not rely on self-invocation.
- Transactions **MUST** declare the smallest service-level unit that owns the invariant. Keep remote calls, message delivery, sleeps, and sandbox execution outside an open transaction.
- `@Async` work **MUST** use an intentional executor, bounded workload, propagated correlation/security context where required, and observable exception handling.
- Scheduled jobs **MUST** tolerate duplicate execution, overlapping nodes, partial failure, and restart. They need bounded batches and explicit retry/lease behavior.
- Publish events only after required state is durable. If delivery must survive process failure, use the project's durable delivery pattern rather than an in-memory event alone.
- External configuration **SHOULD** use validated `@ConfigurationProperties`; avoid scattered `@Value` strings and unsafe defaults for security-sensitive settings.
- Profiles and feature flags **MUST** fail safely for unsupported combinations and be covered by configuration tests when behavior changes.
- Do not expose entities, framework exceptions, stack traces, or internal configuration objects through HTTP responses.
- Filters, interceptors, argument resolvers, and exception handlers **MUST** remain stateless or thread-safe because one instance serves concurrent requests.
- New endpoints, beans, or configuration properties require tests proving startup wiring and the relevant success/failure behavior.
