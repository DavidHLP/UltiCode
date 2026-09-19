---
description: "Maintaining OMP path rules"
globs:
  - ".omp/rules/**/*.md"
---

# OMP rules

Root and nearest `AGENTS.md` own project policy. These files add short, path-scoped risk reminders; apply only the parts relevant to the change.

Use `globs`: the extension currently does not match `paths`. Omit default priority. Do not mix path matching with TTSR triggers or `alwaysApply`; the small JVM diagnostics rule intentionally uses native `alwaysApply` because a process attachment has no reliable file trigger.

Keep equivalent guidance aligned with `.claude/rules/` while retaining each loader’s syntax. Check representative matching and unrelated paths after changing globs. See [rule design](../../docs/DEVELOPMENT.md#编码指南与规则入口).
