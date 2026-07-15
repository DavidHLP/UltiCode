# Runtime diagnostics guardrails

- Treat attached JVM diagnostics as production-affecting even when the command appears read-only; start with the narrowest observation point.
- Bound `watch`, `trace`, `monitor`, and `tt` with `-n N`, normally `N <= 5`; bound `vmtool --action getInstances` with `-l N`.
- Prefer exact class and method names plus read-only probes. Check that a bean exists before retrieving it so diagnostics do not trigger lazy initialization side effects.
- Do not capture credentials, tokens, personal data, full request bodies, or unbounded object graphs in parameters, return values, logs, or transcripts.
- Stop the observation after collecting the minimum evidence needed, and report the signal, scope, and next diagnostic step.
