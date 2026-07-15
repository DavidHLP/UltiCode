---
paths:
  - ".claude/rules/**"
---

# Claude Code rule maintenance

This README is itself path-scoped so it is loaded only while maintaining the rule set.
The layout follows the [Claude Code rules documentation](https://code.claude.com/docs/en/memory#organize-rules-with-claude/rules/).

## Authority

- Root and nested `AGENTS.md` files are the source of truth for UltiCode conventions.
- Rules provide concise, path-triggered working context for Claude Code. They must not override or reproduce an entire project guide.
- Implementation, executable configuration, and tests remain authoritative when documentation drifts.

## Layout

```text
.claude/rules/
├── backend/implementation.md
├── cross-stack/contracts.md
├── database/migrations.md
├── docs/project-documentation.md
├── frontend/console.md
├── frontend/management.md
├── operations/runtime-diagnostics.md
├── operations/runtime-and-infrastructure.md
├── security/trust-boundaries.md
└── shared/packages.md
```

## Design standard

- Keep one topic per file and use descriptive kebab-case names; numeric prefixes do not express precedence.
- Give every rule a narrow, repository-relative `paths` list. A rule without `paths` is loaded in every session and requires a concrete project-wide reason.
- Use only the documented `paths` frontmatter field. Put rationale and provenance in the body when they are genuinely useful.
- Prefer Claude-specific workflow reminders that tell Claude what guide, neighboring implementation, consumers, and checks to inspect.
- Keep rules short and verifiable. Move repeatable multi-step procedures to a skill and hard enforcement to settings or hooks.
- Do not store generic language handbooks, dependency inventories, or facts that Claude can derive directly from source and build files.

## Change checklist

1. Confirm the rule has a single durable purpose and belongs in project context rather than a skill.
2. Test its globs against representative matching and non-matching repository paths.
3. Check for conflicts or duplication with every affected `AGENTS.md`.
4. Keep the file well below 200 lines and review the aggregate context loaded for common edits.
