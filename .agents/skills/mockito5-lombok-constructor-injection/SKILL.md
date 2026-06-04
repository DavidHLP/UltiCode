---
name: mockito5-lombok-constructor-injection
description: Mockito 5 @InjectMocks with Lombok @RequiredArgsConstructor requires all constructor args to have @Mock fields. Trigger when tests throw NPE after adding a new dependency.
---

# Mockito 5 @InjectMocks with Lombok @RequiredArgsConstructor

**Context:** Adding new constructor-injected dependencies to a Spring Boot service using Lombok, existing Mockito tests fail

## Problem

When a class uses Lombok `@RequiredArgsConstructor` and you add a new final dependency, existing `@InjectMocks` tests may inject `null` or fail entirely. Mockito 5 uses **constructor injection** by default for `@InjectMocks`. If any constructor parameter lacks a corresponding `@Mock` field, Mockito cannot instantiate the object and falls back to field injection or fails.

This is especially confusing when:
- The test previously worked (before adding the new dependency)
- The new dependency is not directly used in the test (e.g., `AuditHelper` for logging)
- The error appears as `NullPointerException` during test execution rather than a clear Mockito setup failure

## Solution

**Ensure every constructor parameter has a `@Mock` annotated field:**

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock private DependencyA dependencyA;
    @Mock private DependencyB dependencyB;
    @Mock private NewDependency newDependency;  // MUST add this too!

    @InjectMocks
    private MyService myService;  // Uses @RequiredArgsConstructor
}
```

If the class under test has 5 constructor parameters, you need 5 `@Mock` fields.

**Alternative: Use manual constructor instantiation in `@BeforeEach`**

If maintaining `@Mock` parity is tedious or the constructor is large, bypass `@InjectMocks` entirely:

```java
@BeforeEach
void setUp() {
    myService = new MyService(
        dependencyA,
        dependencyB,
        newDependency
    );
}
```

This is more explicit and avoids Mockito injection magic.

## When to Use

- Adding new dependencies to `@RequiredArgsConstructor` classes
- Upgrading from Mockito 1/2 to Mockito 5
- Tests suddenly start throwing NPEs after a seemingly unrelated service change
- You see `MockitoException: Unable to initialize @InjectMocks` in test logs
