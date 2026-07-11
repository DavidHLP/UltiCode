# C7 Gating — moderationStore + problems.ts God-Store Split

**Date:** 2026-07-11
**Source candidate:** `/tmp/architecture-review-20260711-143159.html` §7 (Worth exploring)
**Red team position:** `.claude/reviews/architecture-review-20260711-143159-review.md` §1.8 + §4 (C7 risks) + §7 (priority #5)

## Decision: deferred, bundled prerequisite unverified

The original review proposed splitting `managementStore.ts` (504 lines)
into three domain stores (queue / reports / appeals) + a `createAsyncResource<T>`
helper. **Red team CR §1.8 + §4** verified the proposal is incomplete:
`problems.ts` (431 lines) has the **same god-store shape** — 5 `ref<>` state,
`tabStates: Map<string, TabState<unknown>>`, `abortControllers: Map<string,
AbortController>`, 15 async functions. Splitting only `moderationStore.ts`
and leaving `problems.ts` untouched **introduces new inconsistency** —
half the admin stores use the helper, the other half doesn't.

## Why deferred

The bundled fix (split BOTH stores + createAsyncResource helper + apply
to both) is a **frontend refactor** that:

1. Touches 935 lines across two Pinia stores + the new helper file.
2. Requires updating all store consumers (controllers / composables).
3. Needs careful migration of the `tabStates` + `abortControllers` data
   flow to ensure abort semantics don't regress (concurrent fetch
   cancellation is load-bearing for the admin UX).
4. Vitest snapshot + behavioural tests for both stores need rewrites.

This is roughly the same scope as the entire C5+C2 refactor combined.

## Gating requirement

The bundled split becomes viable when:

1. **`createAsyncResource<T>()` interface is pinned first** — write the
   helper interface in a draft, mock the boundary, and prove it captures
   both stores' loading/error/abort semantics.
2. **One store is migrated at a time** — moderation first (smaller
   blast radius), then problems.ts.
3. **Migration of consumers is sequenced** — page-level composables
   first, then route guards, then store-level integrations.

These are independent sessions because each step has its own test
surface and regression risk.

## Status

- [ ] Implementation deferred
- [ ] revisit when (a) a fourth admin store needs the same split, or
      (b) admin UI work lands on these stores anyway