---
paths:
  - "console/vitest.config.ts"
  - "console/test/**/*.ts"
  - "console/src/**/{__tests__,test,tests}/**/*.{spec,test}.ts"
  - "management/vitest.config.ts"
  - "management/src/{api,components,composables,i18n,lib,stores,utils,views}/**/*.{spec,test}.ts"
  - "shared/*/vitest.config.ts"
  - "shared/*/{src,__tests__,test,tests}/**/*.{spec,test}.ts"
kind: rules
summary: 'Vitest testing conventions for frontend.'
---

# Vitest rules

- Tests **MUST** assert user-visible behavior, public composable/store behavior, or a concrete regression; assertion-free smoke tests are forbidden.
- Unit tests **MUST NOT** use real backend calls. Mock the application's request helper or the shared package boundary named by the nearest guide.
- When testing Pinia-dependent code, create an isolated Pinia instance per test and use `storeToRefs`/actions as production code would.
- Await Vue updates and promises with `nextTick`, `flushPromises`, or the relevant async helper before asserting rendered state.
- Restore mocks, fake timers, DOM globals, storage, and event listeners after every test. A test must not leak state into the next test.
- Prefer semantic queries and specific assertions over large snapshots. Snapshots are allowed only for stable output where a diff is reviewable.
- Cover loading, empty, success, and failure states when the component owns them. Security-sensitive renderers require malicious-input regression cases.
- Do not over-mock the component under test or assert private refs and implementation-only method calls.
- App test commands do not prove changed shared-package behavior; run the package's own test command as well.
