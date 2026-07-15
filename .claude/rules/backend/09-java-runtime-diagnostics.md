# Runtime diagnostics guardrails

- Treat attached JVM diagnostics as production-affecting even when the command appears read-only; start with the narrowest observation point.
- Bound `watch`, `trace`, `monitor`, and `tt` with `-n N`, normally `N <= 5`; bound `vmtool --action getInstances` with `-l N`.
- Prefer exact class and method names plus read-only probes. Check that a bean exists before retrieving it so diagnostics do not trigger lazy initialization side effects.
- Do not use wildcard class/method patterns on hot paths. Narrow from system/thread evidence to one known observation point before `trace` or `watch`.
- When the same class exists in multiple class loaders, identify the loader hash first and target it explicitly instead of retrying ambiguous commands.
- Do not capture credentials, tokens, personal data, full request bodies, or unbounded object graphs in parameters, return values, logs, or transcripts.
- Stop the observation after collecting the minimum evidence needed, and report the observed signal, exact scope/class loader, conclusion limits, and next diagnostic step.
