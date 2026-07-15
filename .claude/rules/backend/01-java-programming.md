---
paths:
  - "backend-spring/src/**/*.java"
  - "docker/sandbox/harness/java/src/**/*.java"
---

# Java 17 language rules

These defaults apply unless the nearest project guide or an established local pattern is stricter.

## Required

- Code **MUST** compile on Java 17 without preview features or APIs introduced by later JDKs.
- Types **MUST** use `UpperCamelCase`; methods, fields, and locals `lowerCamelCase`; constants `UPPER_SNAKE_CASE`; packages lowercase.
- Names **MUST** describe domain intent. Single-letter names are limited to conventional short loop or coordinate scopes.
- Imports **MUST** be explicit. Raw types, wildcard imports, and unchecked casts/suppressions are forbidden unless narrowly scoped with a reason.
- New injected dependencies **MUST** use constructor injection and should be `private final`; do not add field injection.
- Nullability **MUST** be handled deliberately. Return empty collections instead of `null`; do not call `Optional.get()` without a proven presence check.
- `Optional` **SHOULD** be used for return values, not entity fields, DTO fields, parameters, or serialization contracts.
- Collection ownership **MUST** be clear: copy mutable input when retaining it and do not expose internal mutable collections.
- Streams **MUST NOT** hide side effects, checked-failure handling, or complex branching. Prefer a readable loop when it is clearer.
- Parallel streams **MUST NOT** be introduced in request, transaction, scheduler, or judge paths without measured evidence and an explicit execution model.
- Time-dependent logic **SHOULD** receive a `Clock` or an existing time abstraction so tests remain deterministic.
- Money, scores requiring exact decimals, and identifiers **MUST NOT** use floating-point types.

## Maintainability

- Methods **SHOULD** operate at one level of abstraction and use early returns to keep failure paths visible.
- Boolean parameters with unclear call-site meaning **SHOULD** become an enum, options type, or named method.
- Comments **MUST** explain non-obvious constraints or rationale, not restate the code. Remove stale comments in the same change.
- Do not introduce speculative base classes, utility classes, or interfaces. Extract only after a real boundary or repeated behavior is visible.
