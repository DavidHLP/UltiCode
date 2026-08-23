---
description: Documentation rules for the consolidated project document.
globs:
- AGENTS.md
- '*/AGENTS.md'
- CLAUDE.md
- PROJECT_DOCUMENTATION.md
priority: 100
---

# Project documentation workflow

- Read the Documentation section of the root `AGENTS.md` and identify which file is authoritative for the subject before editing.
- Verify every changed behavior, command, contract, and path against implementation or executable configuration.
- Check the new text against root and nested guides for contradictory scope or duplicated policy.
- Run every documentation validation required by the root guide and inspect generated metadata instead of assuming it is mechanical noise.
- Review links from `PROJECT_DOCUMENTATION.md` and `README.md` so the consolidated content remains discoverable.
