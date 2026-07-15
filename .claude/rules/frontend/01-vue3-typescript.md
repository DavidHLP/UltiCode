---
paths:
  - "console/*.{ts,mts,cts,vue}"
  - "console/{src,test,tests,e2e,scripts}/**/*.{ts,mts,cts,vue}"
  - "management/*.{ts,mts,cts,vue}"
  - "management/{src,test,tests,e2e,scripts}/**/*.{ts,mts,cts,vue}"
  - "shared/*/*.{ts,mts,cts,vue}"
  - "shared/*/{src,test,tests,__tests__}/**/*.{ts,mts,cts,vue}"
---

# TypeScript and Vue 3 rules

- Vue components **MUST** use Composition API with `<script setup lang="ts">` unless editing an established exception.
- New code **MUST** remain type-safe under the nearest `tsconfig`. Do not add `any`, `@ts-ignore`, or double assertions such as `as unknown as T` to bypass a contract.
- Use `unknown` plus a type guard for untrusted data. Use `satisfies` when validating an object shape without widening its inferred type.
- Props and emits **MUST** have explicit types. Do not mutate props or use an emit name/payload that is absent from the declared contract.
- Use `ref` for primitives or replaceable values and `reactive` for stable object identity. Do not destructure reactive state without `toRefs` or Pinia `storeToRefs`.
- Computed getters **MUST** be pure. Side effects belong in an action, explicit event handler, or narrowly scoped watcher with cleanup.
- Watchers **MUST** declare the smallest useful source. Deep or immediate watchers require a concrete reason and must not duplicate initialization.
- Async UI code **MUST** expose loading and failure behavior and prevent stale responses from overwriting newer state when requests can overlap.
- Templates **SHOULD** keep branching and transformation simple. Move reusable logic into typed computed values or composables.
- Dynamic lists **MUST** use stable domain keys; array indexes are not valid keys when items can be inserted, removed, or reordered.
- Do not write unsanitized content to `v-html`, assign unsanitized HTML/URLs to DOM sinks, or bypass the shared rendering packages.
- Composables **MUST** start with `use`, keep lifecycle cleanup with their owner, and avoid hidden application-global mutable state.
- Import shared behavior through declared package entry points. Do not reach into another application's source or undeclared shared internals.
- Follow the nearest formatter and ESLint configuration; do not mix unrelated formatting changes into a functional patch.
