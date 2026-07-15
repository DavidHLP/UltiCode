---
paths:
  - "AGENTS.md"
  - "*/AGENTS.md"
  - "CLAUDE.md"
  - "wiki/**/*.md"
---

# Project documentation workflow

- Keep repository-wide agent policy in root `AGENTS.md`; nested guides contain only durable constraints unique to their subtree.
- Keep `CLAUDE.md` as a short compatibility entry that points to the authoritative guides and path-scoped Claude Code context.
- Verify behavior, commands, contracts, and paths against implementation and executable configuration before documenting them.
- Record durable rationale and non-obvious constraints, not dependency inventories, volatile counts, temporary findings, or plans presented as current architecture.
- Update documentation in the same change as the behavior it describes. After changing wiki content, run `scripts/dev/wiki-manifest.sh` and review the manifest diff.
