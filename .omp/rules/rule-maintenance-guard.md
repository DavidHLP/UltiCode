---
name: "rule-maintenance-guard"
description: "Guard against invalid frontmatter or illegal field mixtures in .omp/rules"
condition:
  - '(?i)astCondition.*globs'
  - '(?i)condition.*globs'
scope: ["tool:edit(*.md)", "tool:write(*.md)"]
interruptMode: always
alwaysApply: false
---

# Rule Maintenance Guard

Do not mix `condition`/`astCondition` with `globs`/`paths` in a single rule.
Path rules MUST use `globs` only. TTSR rules MUST use `condition` only.
