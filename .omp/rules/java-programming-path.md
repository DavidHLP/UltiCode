---
description: Java programming standards for backend source.
globs:
- services/**/src/**/*.java
- '{backend-auth,backend-admin,backend-app}/src/**/*.java'
- docker/sandbox/harness/java/src/**/*.java
priority: 100
---

# Java 17 language rules

These defaults apply unless the nearest project guide or an established local pattern is stricter.

## Required

- Code **MUST** compile on Java 17 without preview features or APIs introduced by later JDKs.
- Types **MUST** use `UpperCamelCase`; methods, fields, and locals `lowerCamelCase`; constants `UPPER_SNAKE_CASE`; packages lowercase.
- Names **MUST** describe domain intent. Single-letter names are limited to conventional short loop or coordinate scopes.
- New imports **MUST** be explicit; do not introduce wildcard imports. Existing wildcard imports may remain when they are outside the task scope. Raw types and new unchecked casts/suppressions are forbidden unless narrowly scoped with a reason.
- New injected dependencies **MUST** use constructor injection and should be `private final`; do not add field injection.
- Overridden methods **MUST** use `@Override`. Do not call deprecated APIs in new code; compatibility shims must identify the supported replacement and removal boundary.
- Nullability **MUST** be handled deliberately. Return empty collections instead of `null`; do not call `Optional.get()` without a proven presence check.
- `Optional` **SHOULD** be used for return values, not entity fields, DTO fields, parameters, or serialization contracts.
- Collection ownership **MUST** be clear: copy mutable input when retaining it and do not expose internal mutable collections.
- Streams **MUST NOT** hide side effects, checked-failure handling, or complex branching. Prefer a readable loop when it is clearer.
- Parallel streams **MUST NOT** be introduced in request, transaction, scheduler, or judge paths without measured evidence and an explicit execution model.
- Time-dependent logic **SHOULD** receive a `Clock` or an existing time abstraction so tests remain deterministic.
- New date/time code **MUST** use `java.time` and immutable, thread-safe `DateTimeFormatter` instances; do not share `SimpleDateFormat` across threads. Formatting patterns must use calendar/proleptic year (`y`/`u`) unless the contract explicitly requires week-based year (`Y`).
- Money, scores requiring exact decimals, and identifiers **MUST NOT** use floating-point types.
- Compare reference values null-safely (`Objects.equals` or a known non-null receiver) and boxed numerics with value equality, not `==`.
- A type that overrides `equals` **MUST** provide a consistent `hashCode`. Custom `Set` elements and `Map` keys must keep equality/hash fields stable while stored in the collection.
- Do not compare floating-point values for exact equality. Construct decimals from strings or `BigDecimal.valueOf`, never `new BigDecimal(double)`.
- Repeated domain literals **MUST** become a named constant or enum at the narrowest shared scope; avoid one global constants dumping ground.
- Use uppercase `L` for long literals so it cannot be confused with `1`.

## Collections and control flow

- Treat `subList`, `keySet`, `values`, `entrySet`, `Arrays.asList`, and `Collections` factory results according to their view/fixed-size/immutable semantics; copy them before incompatible mutation or retention.
- Do not add/remove collection elements inside enhanced `for`. Use an iterator, `removeIf`, or a separate result collection as appropriate.
- Collection-to-array conversion **MUST** preserve the exact component type; prefer `toArray(Type[]::new)` or a zero-length typed array.
- Comparators **MUST** be antisymmetric, transitive, and consistent for equal inputs. Never implement ordering with subtraction that can overflow.
- `if`, loop, and `switch` bodies **MUST** use braces. Switch fall-through must be explicit, externally supplied switch values must be null-safe, and an intentionally exhaustive/default path must be visible.
- Batch inputs and growing in-memory structures **MUST** have a validated upper bound or a streaming/chunking strategy.

## Maintainability

- Methods **SHOULD** operate at one level of abstraction and use early returns to keep failure paths visible.
- Boolean parameters with unclear call-site meaning **SHOULD** become an enum, options type, or named method.
- Comments **MUST** explain non-obvious constraints or rationale, not restate the code. Remove stale comments in the same change.
- Do not introduce speculative base classes, utility classes, or interfaces. Extract only after a real boundary or repeated behavior is visible.
