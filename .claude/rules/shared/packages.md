---
paths:
  - "shared/**/*.{ts,vue,js,mjs,cjs,json,css}"
---

# Shared package workflow

- Read `shared/AGENTS.md` and the changed package's manifest and public entry point before editing.
- Define the package's single owned seam in one sentence. Extend an existing seam when it fits; do not create a generic dumping ground.
- Keep implementation details behind declared entry points and package exports. Inspect every Console and Management consumer before changing a public type, runtime behavior, or import path.
- Preserve app-specific behavior at the application boundary. Shared code must not expose privileged data or force one application's API style onto the other.
- For authentication, HTTP, Markdown, theme, or sandbox contracts, inspect the complete security or initialization pipeline and add adversarial or failure-path regressions where relevant.
- Run scripts declared by the package and the affected consuming applications; do not assume every shared package exposes the same commands.
