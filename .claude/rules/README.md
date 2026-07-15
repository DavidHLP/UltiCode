---
paths:
  - ".claude/rules/**"
---

# Claude Code rule maintenance

This README is itself path-scoped so it is loaded only while maintaining the rule set.
The layout follows the [Claude Code rules documentation](https://code.claude.com/docs/en/memory#organize-rules-with-claude/rules/).

## Authority

- Root and nested `AGENTS.md` files are the source of truth for UltiCode architecture, security invariants, contracts, and workflows.
- Rules provide enforceable language/framework defaults and concise path-triggered working context for Claude Code. They must not override or reproduce an entire project guide.
- Implementation, executable configuration, and tests remain authoritative when documentation drifts.

## Layout

```text
.claude/rules/
├── frontend-rules.md
├── springboot-rules.md
├── backend/implementation.md
├── backend/01-java-programming.md
├── backend/02-java-exception-logging.md
├── backend/03-java-unit-testing.md
├── backend/04-java-security.md
├── backend/05-mysql-database.md
├── backend/06-java-project-structure.md
├── backend/07-java-design.md
├── backend/08-java-code-review-checklist.md
├── backend/09-java-runtime-diagnostics.md
├── backend/code-review-java-spring.md
├── cross-stack/contracts.md
├── database/01-flyway-migrations.md
├── database/02-mysql-coding.md
├── docs/project-documentation.md
├── frontend/console.md
├── frontend/management.md
├── frontend/01-vue3-typescript.md
├── frontend/02-vue-router-pinia.md
├── frontend/03-vitest-testing.md
├── operations/runtime-and-infrastructure.md
├── operations/shell-scripting.md
├── security/trust-boundaries.md
└── shared/packages.md
```

## Design standard

- Keep one topic per file and use descriptive kebab-case names. Numeric prefixes may group a stable related series but never express precedence.
- Use a narrow, repository-relative `paths` list by default. A rule without `paths` is loaded in every session and requires a concrete project-wide reason.
- Use only the documented `paths` frontmatter field. Put rationale and provenance in the body when they are genuinely useful.
- Prefer Claude-specific workflow reminders that tell Claude what guide, neighboring implementation, consumers, and checks to inspect.
- Write language rules as explicit `MUST`, `MUST NOT`, and `SHOULD` constraints. Scope them to the files where Claude can apply and verify them.
- Keep rules short and verifiable. Move repeatable multi-step procedures to a skill and hard enforcement to settings or hooks.
- Do not store generic language handbooks or dependency inventories. Keep only rules that prevent a plausible project regression or ambiguity.

`backend/09-java-runtime-diagnostics.md` is the intentional unconditional exception: Arthas commands are actions with no reliable file path to trigger a conditional rule, and the project exposes the diagnostics MCP during Claude Code sessions.

## Change checklist

1. Confirm the rule has a single durable purpose and belongs in project context rather than a skill.
2. Test its globs against representative matching and non-matching repository paths.
3. Check for conflicts or duplication with every affected `AGENTS.md`.
4. Keep the file well below 200 lines and review the aggregate context loaded for common edits.
