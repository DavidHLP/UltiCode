---
paths:
  - "backend-spring/pom.xml"
  - "backend-spring/src/**/*.java"
---

# Java project-structure rules

- Place new production code in the existing domain module that owns the behavior. Do not create a parallel top-level architecture for a local feature.
- Preserve the dependency direction and roles defined by `backend-spring/AGENTS.md`; controllers, services, mappers, entities, projections, and ports are not interchangeable.
- Cross-module dependencies **MUST** use an existing public seam or a consumer-owned port. Do not import another module's implementation or persistence internals.
- One source file **SHOULD** contain one public top-level type with a matching filename. Keep helpers package-private when they are not part of the module API.
- Role suffixes **MUST** match behavior (`Controller`, `Service`, `Mapper`, `Entity`, `DTO`, `VO`, `Projection`, `Port`, `Adapter`). Do not use a misleading suffix to bypass a boundary.
- Do not turn `common`, `util`, or `helper` packages into dumping grounds. Shared code needs a cohesive responsibility and more than accidental reuse.
- Configuration belongs with the feature it configures unless it is genuinely application-wide. Avoid component scanning or bean definitions that depend on package accidents.
- New libraries **MUST** be declared in Maven with a concrete need. Do not vendor jars, duplicate an existing dependency's capability, or add a framework for one helper function.
- Production dependencies **MUST NOT** use mutable `SNAPSHOT` versions. One group/artifact must resolve to one intentional version across the build.
- After adding or upgrading a dependency, inspect the resolved dependency tree and verify unexpected transitive version changes rather than accepting them implicitly.
- Generated output, local runtime state, credentials, and build artifacts **MUST NOT** be added under source packages.
