---
paths:
  - ".claude/rules/**"
---

# Claude Code rule maintenance

This README is itself path-scoped so it is loaded only while maintaining the rule set.
The layout follows the [Claude Code rules documentation](https://code.claude.com/docs/en/memory#organize-rules-with-claude/rules/).

## Authority

- Root and nested `AGENTS.md` files are the source of truth for UltiCode architecture, security invariants, contracts, and workflows.
- Rules provide explicit language/framework guardrails and concise path-triggered working context for Claude Code. They must not override or reproduce an entire project guide.
- Implementation, executable configuration, and tests remain authoritative when documentation drifts.

## Layout

`.claude/rules/` is the Claude Code rule set. `.omp/rules/` is a separate OMP rule set; keep their files, frontmatter, and references independent.

```text
.claude/rules/
├── backend/
│   ├── spring-boot.md
│   ├── java-programming.md
│   ├── java-exception-logging.md
│   ├── java-unit-testing.md
│   ├── java-security.md
│   ├── mysql-database.md
│   ├── java-project-structure.md
│   ├── java-design.md
│   ├── java-code-review-checklist.md
│   ├── java-runtime-diagnostics.md
│   ├── implementation.md
│   └── spring-code-review.md
├── cross-stack/
│   └── contracts.md
├── database/
│   ├── flyway-migrations.md
│   └── mysql-coding.md
├── docs/
│   └── project-documentation.md
├── frontend/
│   ├── conventions.md
│   ├── vue3-typescript.md
│   ├── vue-router-pinia.md
│   ├── vitest-testing.md
│   ├── console.md
│   └── management.md
├── operations/
│   ├── runtime-and-infrastructure.md
│   └── shell-scripting.md
├── packages/
│   └── shared-packages.md
└── security/
    └── trust-boundaries.md
```

## Design standard

- Keep one topic per file and use descriptive kebab-case names without numeric ordering prefixes.
- Use a narrow, repository-relative `paths` list by default. A rule without `paths` is loaded in every session and requires a concrete project-wide reason.
- Use only the documented `paths` frontmatter field. Put rationale and provenance in the body when they are genuinely useful.
- Prefer Claude-specific workflow reminders that tell Claude what guide, neighboring implementation, consumers, and checks to inspect.
- Write language rules as explicit `MUST`, `MUST NOT`, and `SHOULD` constraints. Scope them to the files where Claude can apply and verify them.
- When consolidating legacy rules, preserve concrete failure modes and project-relevant counterexamples; remove obsolete framework advice, arbitrary thresholds, and rules contradicted by current code/configuration.
- Keep rules short and verifiable. Move repeatable multi-step procedures to a skill and hard enforcement to settings or hooks.
- Do not store generic language handbooks or dependency inventories. Keep only rules that prevent a plausible project regression or ambiguity.

`backend/java-runtime-diagnostics.md` is the intentional unconditional exception: Arthas commands are actions with no reliable file path to trigger a conditional rule, and the project exposes the diagnostics MCP during Claude Code sessions.

## Change checklist

1. Confirm the rule has a single durable purpose and belongs in project context rather than a skill.
2. Test its globs against representative matching and non-matching repository paths.
3. Check for conflicts or duplication with every affected `AGENTS.md`.
4. Keep the file well below 200 lines and review the aggregate context loaded for common edits.
