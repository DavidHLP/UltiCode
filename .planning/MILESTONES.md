# Milestones

## v1.0 Technical Debt Remediation (Shipped: 2026-04-16)

**Phases completed:** 4 phases, 11 plans, 20 tasks

**Key accomplishments:**

- Replaced broken regex-based XssFilter input stripping with pass-through filter; added OWASP Java Encoder dependency for future output encoding (SEC-06)
- CsrfValidationFilter servlet filter validates CSRF tokens after JWT auth in the Spring Security chain, replacing the WebMvc-layer CsrfInterceptor
- @PostConstruct fail-fast validation on JwtProperties and removal of dead UserDetailsServiceImpl placeholder
- Password reset flow migrated from Redis to DB-stored BCrypt token hashes with EmailServiceImpl integration for actual email delivery
- Admin rejudge/batch-rejudge endpoints enqueue LOW-priority judge jobs with retryCount tracking, batch size limit of 50, and 5 req/min rate limiting
- Docker sandbox hardened with --cap-drop ALL and custom seccomp profile blocking ptrace/mount/keyctl/unshare/setns/clone-namespaces for all 5 supported languages
- 48 unit tests covering JWT token generation/validation, CSRF lifecycle, login/register/refresh flows, and password reset with session revocation
- Testcontainers BOM 1.21.3 in pom.xml, 18 new unit tests for SubmissionServiceImpl (8) and CodeExecutionService (10), AdminSubmissionServiceImplTest verified complete for Phase 2
- 5 Testcontainers integration tests for SubmissionServiceImpl verifying persistence to real MySQL and queue failure fallback, with manual MyBatis-Plus SqlSessionFactory setup avoiding full Spring context
- 8 oversized console Vue components split into 34 co-located sub-components and 8 composables with all parents under 500 lines
- Split 5 oversized management components (1224, 881, 768, 627, 602 lines) and 1 Pinia store (600 lines) into 25 co-located sub-components, 6 composables, and 5 domain store modules, all under 500 lines

---
