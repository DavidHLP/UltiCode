---
paths:
  - "backend-spring/pom.xml"
  - "backend-spring/src/**/*.{java,xml,yml,yaml,properties}"
---

# Backend implementation workflow

- Read `backend-spring/AGENTS.md` before editing; it supplements the root guide and is authoritative for this subtree.
- Use the codebase knowledge graph to inspect the affected module, inbound callers, outbound dependencies, and nearby tests before choosing a seam.
- Follow the complete existing request path instead of inferring behavior from one class. Include executable configuration and persistence code when they affect the change.
- Extend the module's existing architecture. If a change crosses a module boundary, inspect the consumer-owned interface and all implementations rather than reaching into another module's internals.
- Treat a boundary change as a coordinated change: validate inputs, preserve response/error contracts, update mappings and consumers, and cover important failure paths.
- Review transaction scope, race-sensitive state, authorization, resource cleanup, and null handling whenever the changed behavior touches them.
- Add or update the closest focused tests. Select unit, integration, and verification commands from the backend guide according to risk; remember that `*IT.java` tests require explicit selection.
- Before completion, inspect only the task diff for contract drift, accidental architectural shortcuts, and unrelated formatting changes.
