---
name: java-map-of-null-safety
description: Java Map.of() throws NPE on null values; use requireNonNullElse or HashMap for nullable data. Trigger when building maps from entity fields or audit logs where nulls may occur.
---

# Java Map.of() Null Safety

**Context:** Building audit log oldValues/newValues maps where entity fields may be null

## Problem

`Map.of()` (Java 9+) throws `NullPointerException` if any value is `null`. This is a common trap when building maps from entity fields that may not be set (e.g., `flaggedReason`, `description`, optional metadata).

```java
// Throws NPE if solution.getFlaggedReason() is null
Map.of("isFlagged", true, "flaggedReason", solution.getFlaggedReason());
```

## Solution

**For maps where values might be null** (e.g., capturing pre-mutation state):

Use `HashMap` which allows null values:

```java
Map<String, Object> oldValues = new HashMap<>();
oldValues.put("isFlagged", solution.getIsFlagged());
oldValues.put("flaggedReason", solution.getFlaggedReason());  // null is OK
```

**For maps where values should never be null** (e.g., post-mutation state):

Use `Objects.requireNonNullElse()` to provide defaults:

```java
Map.of(
    "title", Objects.requireNonNullElse(solution.getTitle(), ""),
    "flaggedReason", Objects.requireNonNullElse(reason, "")
);
```

**Audit logging pattern:**

```java
// oldValues: may contain nulls -> HashMap
Map<String, Object> oldValues = new HashMap<>();
oldValues.put("name", contest.getName());
oldValues.put("description", contest.getDescription());

// newValues: should be fully specified -> Map.of with defaults
Map<String, Object> newValues = Map.of(
    "name", Objects.requireNonNullElse(dto.getName(), ""),
    "description", Objects.requireNonNullElse(dto.getDescription(), "")
);

auditHelper.log(action, entity, id, userId, oldValues, newValues);
```

## When to Use

- Building maps from database entities with nullable columns
- Audit logging (oldValues before mutation, newValues after)
- Any `Map.of()` call where a value comes from external/untrusted data
- Prefer `HashMap` when null is semantically meaningful; prefer `requireNonNullElse` when null indicates missing data that should default to empty string/0/false
