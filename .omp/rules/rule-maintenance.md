---
name: rule-maintenance
description: Rules for maintaining this omp project rule set and keeping guidance scoped, short, and verifiable.
globs:
  - .omp/**/*.md
  - AGENTS.md
  - '**/AGENTS.md'
condition: ["(?i)rule|RULES|frontmatter|globs|TTSR"]
interruptMode: never
alwaysApply: false
---

# Rule maintenance

- Each rule file MUST have one durable purpose and explicit `name` and `description` frontmatter.
- Use `globs` to scope rules narrowly; do not make a rule global without a concrete project-wide reason.
- Prefer explicit `MUST`, `MUST NOT`, and `SHOULD` constraints that prevent a plausible regression.
- Keep rules concise and verifiable. Move repeatable procedures to a skill and hard enforcement to settings or hooks.
- Do not store generic language handbooks, dependency inventories, or speculative architecture.
- Check changed rules against the nearest `AGENTS.md` for conflicts and duplication.
- Test representative matching and non-matching paths when changing `globs`.
- Keep `RULES.md` short: it is always-applied sticky context; put path-specific detail in `.omp/rules/`.
