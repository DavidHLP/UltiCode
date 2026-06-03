---
name: cross-stack-dto-granularity-alignment
description: "Systematic method to analyze and align DTO types, API endpoints, and enums between frontend and backend codebases"
user-invocable: false
origin: auto-extracted
---

# Cross-Stack DTO Granularity Alignment

**Extracted:** 2026-05-20
**Context:** When frontend and backend evolve independently, DTO fields, enum values, and API endpoints drift out of sync. This pattern provides a systematic method to audit and document alignment gaps.

## Problem
Frontend TypeScript types and backend Java DTOs drift over time: missing fields, mismatched enum values, phantom types (frontend types with no backend endpoint), and unused backend endpoints. Manual spot-checking misses these gaps.

## Solution

### Step 1: Parallel Code Exploration
Launch parallel agents to explore backend and frontend independently:
- **Agent 1 (Backend):** Find all controllers, DTOs (request + response), enums, and service methods. List every API endpoint with HTTP method, path, parameters, and return type.
- **Agent 2 (Frontend):** Find all API functions, type definitions, and page components. List every API call with method, URL, and typed parameters/returns.
- **Agent 3 (Other frontends):** Check if additional frontend apps (e.g., user-facing vs admin) consume the same APIs.

### Step 2: Field-Level DTO Comparison
For each shared DTO, create a comparison table:
| Field | Backend Type | Frontend Type | Alignment |
Backend `String` vs frontend enum is a common mismatch — frontend is stricter/correct, backend should adopt enums.

### Step 3: Enum Value Audit
Compare every enum across all frontends and backend. Tabulate which values exist where. Common pattern: admin frontend has all values, user frontend is missing some, backend has all.

### Step 4: Identify Ghost Types and Orphan Endpoints
- **Ghost types**: Frontend types defined but never backed by a backend endpoint (dead code or future scaffolding)
- **Orphan endpoints**: Backend endpoints no frontend calls (unused or undiscovered)

### Step 5: Service Layer Granularity
Flag backend methods over ~50 lines or with 5+ conditional branches as candidates for decomposition (strategy pattern, etc.).

### Step 6: Generate Prioritized Report
Output a structured report to `docs/` with:
- Full alignment tables
- Mismatch issues ranked by severity (P1-P4)
- Specific file:line references for each issue

## When to Use
- After a module has been developed independently on frontend and backend
- Before a major release or API versioning effort
- When debugging "type mismatch" or "missing field" errors at the FE/BE boundary
- When onboarding to a new full-stack module
