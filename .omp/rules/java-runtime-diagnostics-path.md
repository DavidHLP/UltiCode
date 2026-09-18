---
description: "Safety only when using live JVM diagnostics"
alwaysApply: true
---

# JVM diagnostics

Only when attaching runtime diagnostics: use narrow class/method targets and bounded observation counts/time; select the class loader when ambiguous. Avoid side effects and sensitive object dumps, and stop probes when the needed evidence is collected. Obtain authorization before mutating a live process.
