---
paths:
  - "services/pom.xml"
  - "services/**/pom.xml"
  - "services/**/src/**/*.java"
kind: rules
summary: 'Java project structure and module layout.'
---

# Java project-structure rules

- Place new production code in the existing domain module or owner service that owns the behavior (`auth`, `admin`, `app`, `submission`, `notification`, `search`, `judge`, `platform`, `api`). Do not create a parallel top-level architecture for a local feature.
- Preserve the dependency direction and roles defined by root `AGENTS.md` and `services/AGENTS.md`: `platform/` provides shared utilities/security, `api/` declares Dubbo RPC contracts, and owner services maintain isolated domain and schema boundaries.
- Cross-service or cross-module dependencies **MUST** use an existing public seam, consumer-owned port, or Dubbo RPC contract in `services/api/`. Do not import another owner's implementation or persistence internals.
- Use the narrowest visibility compatible with framework binding/proxying and the module contract. Fields are private unless an established framework or API contract requires otherwise; classes, constructors, and methods outside an external contract **SHOULD NOT** be public or widened for test convenience.
- One source file **SHOULD** contain one public top-level type with a matching filename. Keep helpers package-private when they are not part of the module API.
- Role suffixes **MUST** match behavior (`Controller`, `Service`, `Mapper`, `Entity`, `DTO`, `VO`, `Projection`, `Port`, `Adapter`). Do not use a misleading suffix to bypass a boundary.
- Do not turn `common`, `util`, or `helper` packages into dumping grounds. Shared code needs a cohesive responsibility and more than accidental reuse.
- Configuration belongs with the feature it configures unless it is genuinely application-wide. Avoid component scanning or bean definitions that depend on package accidents.
- New libraries **MUST** be declared in Maven with a concrete need. Do not vendor jars, duplicate an existing dependency's capability, or add a framework for one helper function.
- Production dependencies **MUST NOT** use mutable `SNAPSHOT` versions. One group/artifact must resolve to one intentional version across the build.
- After adding or upgrading a dependency, inspect the resolved dependency tree and verify unexpected transitive version changes rather than accepting them implicitly.
- Generated output, local runtime state, credentials, and build artifacts **MUST NOT** be added under source packages.
