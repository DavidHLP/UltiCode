---
paths:
  - "services/pom.xml"
  - "services/**/pom.xml"
  - "services/**/src/**/*.{java,xml,yml,yaml,properties}"
kind: rules
summary: 'Backend implementation workflow and conventions.'
---

# Backend implementation workflow

- Read the root `AGENTS.md` before editing root-level owner services; read `services/AGENTS.md` when editing shared reactor modules.
- Use the codebase knowledge graph to inspect the affected module, inbound callers, outbound dependencies, and nearby tests before choosing a seam.
- Build a change map covering the entry point, business path, persistence path, executable configuration, consumers, and closest tests; omit a layer only after confirming it is unaffected.
- Compare the proposed seam with a representative module already in the repository. If the task requires a different shape, state why it still satisfies the guides before implementing it.
- After editing, repeat inbound and outbound impact tracing to catch callers or implementations made stale by the change.
- Select checks from the backend guide according to the changed risk boundary, then inspect only the task diff for missed consumers and unrelated rewrites.
