---
description: Documentation governance for the canonical docs tree.
globs:
- AGENTS.md
- '*/AGENTS.md'
- CLAUDE.md
- 'docs/**/*.md'
priority: 100
---

# Project documentation workflow

- Read the Documentation section of the root `AGENTS.md` and start with `docs/index.md` to identify the canonical source for the subject before editing.
- Verify every changed behavior, command, contract, and path against implementation or executable configuration.
- Check the new text against root and nested guides for contradictory scope or duplicated policy.
- Run every documentation validation required by the root guide and inspect generated metadata instead of assuming it is mechanical noise.
- Review links from `docs/index.md`, the relevant canonical document, and `README.md` so the documentation remains discoverable.
