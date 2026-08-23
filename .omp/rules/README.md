---
description: "Path-scoped rule maintenance conventions for omp-path-rules"
globs: [".omp/rules/**/*.md", "AGENTS.md", "**/AGENTS.md"]
priority: 120
---

# Path rule maintenance

- Use `globs` or `paths` for pre-inference guidance; do not add `condition`, `astCondition`, `ast_condition`, `ttsr_trigger`, or `ttsrTrigger` to a path rule.
- Keep runtime inspection or interruption in a separate native TTSR rule. Do not duplicate the same rule body across the path and TTSR files.
- Keep each rule focused, repository-relative, concise, and verifiable. Read the nearest `AGENTS.md` before editing.
- Validate representative matching and non-matching paths after changing a rule.
- Use `priority` only when multiple path rules need deterministic ordering; default to `100` otherwise.
- Treat implementation, executable configuration, tests, and `AGENTS.md` as authoritative when rules disagree with them.
