# Problem Detail Data Architecture Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor problem detail page to use lightweight APIs with independent data fetching per tab, eliminating redundant API calls and simplifying state management.

**Architecture:** Add 4 lightweight backend API endpoints (header, description, code, cases), flatten frontend store structure with independent loading/error states, implement AbortController for race condition handling, and parallel load header + initial tab data on page entry.

**Tech Stack:** NestJS, Prisma, Vue 3, Pinia, Axios

---

## File Structure

### Backend Files to Create/Modify
- `backend/src/admin/dto/problem-tab-responses.dto.ts` - Create response DTOs (NEW FILE)
- `backend/src/admin/controllers/admin-problem.controller.ts` - Add 4 new routes

### Frontend Files to Modify
- `management/src/utils/request.ts` - Add AbortSignal support
- `management/src/api/admin/problems.ts` - Add 4 new API functions + types
- `management/src/stores/admin/problems.ts` - Refactor state structure
- `management/src/views/problems/ProblemDetailView.vue` - Refactor data loading logic

### Frontend Files - No Changes
- `management/src/views/problems/components/DescriptionDisplay.vue` - Keep props interface
- `management/src/views/problems/components/CodeDisplay.vue` - Keep props interface
- `management/src/views/problems/components/CasesDisplay.vue` - Keep props interface

---

## Chunk 1: Backend API Implementation

### Task 1: Create Response DTOs

**Files:**
- Create: `backend/src/admin/dto/problem-tab-responses.dto.ts`

**Important:** This file consolidates all tab response DTOs into a single file for cleaner organization.

- [ ] **Step 1: Create the DTO file with all response types**

```typescript
// backend/src/admin/dto/problem-tab-responses.dto.ts
import { ApiProperty } from '@nestjs/swagger';
import { Difficulty, ProblemStatus } from '@prisma/client';

export class ProblemHeaderResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  title: string;

  @ApiProperty()
  slug: string;

  @ApiProperty({ enum: ['EASY', 'MEDIUM', 'HARD'] })
  difficulty: string;

  @ApiProperty({ enum: ['solved', 'attempted', 'todo'] })
  status: string;

  @ApiProperty()
  is_premium: boolean;

  @ApiProperty()
  is_published: boolean;

  @ApiProperty({ required: false, nullable: true })
  published_at: Date | null;
}

export class ProblemExampleDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  input: string;

  @ApiProperty()
  output: string;

  @ApiProperty({ required: false, nullable: true })
  explanation?: string;

  @ApiProperty()
  order: number;
}

export class ProblemTagDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  label: string;
}

class ProblemDetailForDescription {
  @ApiProperty({ required: false, nullable: true })
  summary?: string;

  @ApiProperty({ required: false, nullable: true })
  content?: string;

  @ApiProperty({ required: false, type: [String] })
  constraints_json?: string[];

  @ApiProperty({ required: false, type: [String] })
  hints?: string[];
}

export class ProblemDescriptionResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  title: string;

  @ApiProperty()
  slug: string;

  @ApiProperty()
  difficulty: string;

  @ApiProperty()
  is_premium: boolean;

  @ApiProperty()
  is_published: boolean;

  @ApiProperty({ required: false, nullable: true })
  detail?: ProblemDetailForDescription;

  @ApiProperty({ type: [ProblemTagDto] })
  tags: ProblemTagDto[];

  @ApiProperty({ type: [ProblemExampleDto], required: false })
  examples?: ProblemExampleDto[];

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;

  @ApiProperty({ required: false, nullable: true })
  published_at?: Date;
}

class ProblemLanguageDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  language: string;

  @ApiProperty()
  value: string;

  @ApiProperty({ required: false, nullable: true })
  style?: string;

  @ApiProperty()
  starter_code: string;
}

export class ProblemCodeResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ type: [ProblemLanguageDto], required: false })
  languages?: ProblemLanguageDto[];
}

class ProblemDetailForCases {
  @ApiProperty({ required: false, type: [String] })
  constraints_json?: string[];

  @ApiProperty({ required: false, type: [String] })
  hints?: string[];
}

export class ProblemCasesResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ type: [ProblemExampleDto], required: false })
  examples?: ProblemExampleDto[];

  @ApiProperty({ required: false, nullable: true })
  detail?: ProblemDetailForCases;

  @ApiProperty({ type: [ProblemTagDto], required: false })
  tags?: ProblemTagDto[];
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add backend/src/admin/dto/problem-tab-responses.dto.ts
git commit -m "feat(backend): add response DTOs for problem tab APIs"
```

---

### Task 2: Add Header API Endpoint

**Files:**
- Modify: `backend/src/admin/controllers/admin-problem.controller.ts`

**Important:** Place this route BEFORE the generic `@Get(':id')` route to avoid NestJS route conflicts.

- [ ] **Step 1: Add imports to controller**

Add these imports to the existing imports section:

```typescript
import { NotFoundException } from '@nestjs/common';
import { ApiOperation, ApiResponse } from '@nestjs/swagger';
import { ProblemHeaderResponseDto, ProblemDescriptionResponseDto, ProblemCodeResponseDto, ProblemCasesResponseDto } from '../dto/problem-tab-responses.dto';
```

- [ ] **Step 2: Add getHeader endpoint to controller**

Add this method to `AdminProblemController` class, **before** the `@Get(':id')` route:

```typescript
  @Get(':id/header')
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  @ApiOperation({ summary: 'Get problem header data' })
  @ApiResponse({ status: 200, description: 'Problem header data' })
  @ApiResponse({ status: 404, description: 'Problem not found' })
  async getHeader(
    @Param('id') id: string,
  ): Promise<ProblemHeaderResponseDto> {
    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(id) },
      select: {
        id: true,
        title: true,
        slug: true,
        difficulty: true,
        status: true,
        is_premium: true,
        is_published: true,
        published_at: true,
      },
    });

    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    return {
      id: problem.id.toString(),
      title: problem.title,
      slug: problem.slug,
      difficulty: mapDifficultyToFrontend(problem.difficulty),
      status: problem.status,
      is_premium: problem.is_premium,
      is_published: problem.is_published,
      published_at: problem.published_at,
    };
  }
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add backend/src/admin/controllers/admin-problem.controller.ts
git commit -m "feat(backend): add getProblemHeader API endpoint"
```

---

### Task 3: Add Description API Endpoint

**Files:**
- Modify: `backend/src/admin/controllers/admin-problem.controller.ts`

**Important:** Place this route BEFORE the generic `@Get(':id')` route.

- [ ] **Step 1: Add getDescription endpoint**

Add this method to `AdminProblemController` class, **before** the `@Get(':id')` route:

```typescript
  @Get(':id/description')
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  @ApiOperation({ summary: 'Get problem description data' })
  @ApiResponse({ status: 200, description: 'Problem description data' })
  @ApiResponse({ status: 404, description: 'Problem not found' })
  async getDescription(
    @Param('id') id: string,
  ): Promise<ProblemDescriptionResponseDto> {
    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(id) },
      include: {
        detail: true,
        tagRelations: { include: { tag: true } },
        examples: { orderBy: { example_order: 'asc' } },
      },
    });

    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    return {
      id: problem.id.toString(),
      title: problem.title,
      slug: problem.slug,
      difficulty: mapDifficultyToFrontend(problem.difficulty),
      is_premium: problem.is_premium,
      is_published: problem.is_published,
      detail: problem.detail
        ? {
            summary: problem.detail.summary,
            content: problem.detail.content, // Note: content, not summary
            constraints_json: problem.detail.constraints_json as string[] | undefined,
            hints: problem.detail.hints as string[] | undefined,
          }
        : undefined,
      tags: problem.tagRelations.map((tr) => ({
        id: tr.tag.id,
        label: tr.tag.label,
      })),
      examples: problem.examples.map((ex) => ({
        id: ex.id,
        input: ex.input_text,
        output: ex.output_text,
        explanation: ex.explanation ?? undefined,
        order: ex.example_order,
      })),
      created_at: problem.published_at || new Date(),
      updated_at: problem.detail?.updated_at || new Date(),
      published_at: problem.published_at ?? undefined,
    };
  }
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add backend/src/admin/controllers/admin-problem.controller.ts
git commit -m "feat(backend): add getProblemDescription API endpoint"
```

---

### Task 4: Add Code API Endpoint

**Files:**
- Modify: `backend/src/admin/controllers/admin-problem.controller.ts`

**Important:** Place this route BEFORE the generic `@Get(':id')` route.

- [ ] **Step 1: Add getCode endpoint**

Add this method to `AdminProblemController` class, **before** the `@Get(':id')` route:

```typescript
  @Get(':id/code')
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  @ApiOperation({ summary: 'Get problem code templates' })
  @ApiResponse({ status: 200, description: 'Problem code templates' })
  @ApiResponse({ status: 404, description: 'Problem not found' })
  async getCode(
    @Param('id') id: string,
  ): Promise<ProblemCodeResponseDto> {
    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(id) },
      select: {
        id: true,
        languages: true,
      },
    });

    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    return {
      id: problem.id.toString(),
      languages: problem.languages.map((lang) => ({
        id: lang.id,
        language: lang.label,
        value: lang.value,
        style: lang.style ?? undefined,
        starter_code: lang.starter_code,
      })),
    };
  }
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add backend/src/admin/controllers/admin-problem.controller.ts
git commit -m "feat(backend): add getProblemCode API endpoint"
```

---

### Task 5: Add Cases API Endpoint

**Files:**
- Modify: `backend/src/admin/controllers/admin-problem.controller.ts`

**Important:** Place this route BEFORE the generic `@Get(':id')` route.

- [ ] **Step 1: Add getCases endpoint**

Add this method to `AdminProblemController` class, **before** the `@Get(':id')` route:

```typescript
  @Get(':id/cases')
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  @ApiOperation({ summary: 'Get problem test cases data' })
  @ApiResponse({ status: 200, description: 'Problem test cases data' })
  @ApiResponse({ status: 404, description: 'Problem not found' })
  async getCases(
    @Param('id') id: string,
  ): Promise<ProblemCasesResponseDto> {
    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(id) },
      include: {
        detail: { select: { constraints_json: true, hints: true } },
        tagRelations: { include: { tag: true } },
        examples: { orderBy: { example_order: 'asc' } },
      },
    });

    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    return {
      id: problem.id.toString(),
      examples: problem.examples.map((ex) => ({
        id: ex.id,
        input: ex.input_text,
        output: ex.output_text,
        explanation: ex.explanation ?? undefined,
        order: ex.example_order,
      })),
      detail: problem.detail
        ? {
            constraints_json: problem.detail.constraints_json as string[] | undefined,
            hints: problem.detail.hints as string[] | undefined,
          }
        : undefined,
      tags: problem.tagRelations.map((tr) => ({
        id: tr.tag.id,
        label: tr.tag.label,
      })),
    };
  }
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 3: Run backend tests**

Run: `cd backend && pnpm test -- --testPathPattern="admin-problem.controller"`
Expected: All tests pass (or note that new tests will be added in follow-up)

- [ ] **Step 4: Commit**

```bash
git add backend/src/admin/controllers/admin-problem.controller.ts
git commit -m "feat(backend): add getProblemCases API endpoint"
```

---

## Chunk 2: Frontend API Layer

### Task 6: Add AbortSignal Support to Request Utils

**Files:**
- Modify: `management/src/utils/request.ts`

- [ ] **Step 1: Add signal parameter to apiGet**

Modify the `apiGet` function to accept AbortSignal:

```typescript
export async function apiGet<T = unknown>(path: string, init?: RequestConfig & { signal?: AbortSignal }): Promise<T> {
  const { signal, ...axiosConfig } = (init || {}) as any;
  return service.get<T, T>(path, {
    ...axiosConfig,
    signal,
  });
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add management/src/utils/request.ts
git commit -m "feat(frontend): add AbortSignal support to apiGet"
```

---

### Task 7: Add New Types and API Functions

**Files:**
- Modify: `management/src/api/admin/problems.ts`

- [ ] **Step 1: Add new type definitions after existing types**

Add these types after the `ProblemLanguage` interface:

```typescript
// ========== Tab-specific Types ==========

export interface HeaderData {
  id: string
  title: string
  slug: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  status: ProblemStatus
  is_premium: boolean
  is_published: boolean
  published_at?: Date
}

export interface DescriptionData {
  id: string
  title: string
  slug: string
  difficulty: string
  is_premium: boolean
  is_published: boolean
  detail?: {
    summary?: string
    content?: string
    constraints_json?: string[]
    hints?: string[]
  }
  tags: Array<{ id: string; label: string }>
  examples?: ProblemExample[]
  created_at: Date
  updated_at: Date
  published_at?: Date
}

export interface CodeData {
  id: string
  languages?: Array<{
    id: string
    language: string
    value: string
    style?: string
    starter_code: string
  }>
}

export interface CasesData {
  id: string
  examples?: Array<{
    id: string
    input: string
    output: string
    explanation?: string
    order: number
  }>
  detail?: {
    constraints_json?: string[]
    hints?: string[]
  }
  tags?: Array<{ id: string; label: string }>
}
```

- [ ] **Step 2: Add new API functions to problemsApi object**

Add these functions inside the `problemsApi` object:

```typescript
  // ========== Tab-specific APIs ==========

  async getHeader(id: string, signal?: AbortSignal): Promise<HeaderData> {
    return apiGet<HeaderData>(`/admin/problems/${id}/header`, { signal })
  },

  async getDescription(id: string, signal?: AbortSignal): Promise<DescriptionData> {
    return apiGet<DescriptionData>(`/admin/problems/${id}/description`, { signal })
  },

  async getCode(id: string, signal?: AbortSignal): Promise<CodeData> {
    return apiGet<CodeData>(`/admin/problems/${id}/code`, { signal })
  },

  async getCases(id: string, signal?: AbortSignal): Promise<CasesData> {
    return apiGet<CasesData>(`/admin/problems/${id}/cases`, { signal })
  },
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add management/src/api/admin/problems.ts
git commit -m "feat(frontend): add tab-specific types and API functions for problem detail"
```

---

## Chunk 3: Frontend Store Refactor

### Task 8: Remove Old Types and Add New State

**Files:**
- Modify: `management/src/stores/admin/problems.ts`

**Important:** The existing `DescriptionData`, `CodeData`, `CasesData`, `TabLoadingStates`, and `TabDataCache` types in the store file should be removed, as they will be imported from the API file instead.

- [ ] **Step 1: Remove old type definitions from store**

Remove these type definitions that will be imported from the API file instead:

```typescript
// Remove these:
// export interface DescriptionData { ... }
// export interface CodeData { ... }
// export interface CasesData { ... }
// export interface TabLoadingStates { ... }
// export interface TabDataCache { ... }
```

- [ ] **Step 2: Update imports to use types from API**

```typescript
import {
  problemsApi,
  type Problem,
  type ProblemQueryParams,
  type CreateProblemDto,
  type UpdateProblemDto,
  type BulkProblemActionDto,
  type ProblemExample,
  type ProblemLanguage,
  type HeaderData,
  type DescriptionData,
  type CodeData,
  type CasesData,
} from '@/api/admin/problems'
```

- [ ] **Step 3: Replace state definitions with flat structure**

Replace the existing state definitions (tabData, tabLoading, currentProblem, loadedProblemId) with:

```typescript
// ========== Header State ==========
const headerData = ref<HeaderData | null>(null)
const headerLoading = ref(false)
const headerError = ref<string | null>(null)

// ========== Description Tab State ==========
const descriptionData = ref<DescriptionData | null>(null)
const descriptionLoading = ref(false)
const descriptionError = ref<string | null>(null)

// ========== Code Tab State ==========
const codeData = ref<CodeData | null>(null)
const codeLoading = ref(false)
const codeError = ref<string | null>(null)

// ========== Cases Tab State ==========
const casesData = ref<CasesData | null>(null)
const casesLoading = ref(false)
const casesError = ref<string | null>(null)

// ========== Abort Controllers ==========
const abortControllers = ref<Map<string, AbortController>>(new Map())
```

- [ ] **Step 4: Commit**

```bash
git add management/src/stores/admin/problems.ts
git commit -m "refactor(frontend): flatten store state structure for problem detail"
```

---

### Task 9: Add AbortController Helpers and Fetch Functions

**Files:**
- Modify: `management/src/stores/admin/problems.ts`

- [ ] **Step 1: Add AbortController helper functions**

Add these functions after the state definitions:

```typescript
// ========== AbortController Helpers ==========

function getAbortController(key: string): AbortController {
  let controller = abortControllers.value.get(key)
  if (controller) {
    controller.abort()
  }
  controller = new AbortController()
  abortControllers.value.set(key, controller)
  return controller
}

function abortAllRequests() {
  abortControllers.value.forEach((controller) => controller.abort())
  abortControllers.value.clear()
}

function extractErrorMessage(err: unknown): string {
  return (
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
    (err as Error)?.message ||
    'An error occurred'
  )
}
```

- [ ] **Step 2: Add new fetch functions (replacing old ones)**

Replace the existing `fetchDescriptionData`, `fetchCodeData`, `fetchCasesData` functions with:

```typescript
// ========== Tab Fetch Functions ==========

async function fetchHeader(id: string): Promise<HeaderData | null> {
  const controller = getAbortController('header')
  headerLoading.value = true
  headerError.value = null

  try {
    const data = await problemsApi.getHeader(id, controller.signal)
    headerData.value = data
    return data
  } catch (err: unknown) {
    if ((err as Error).name === 'AbortError') {
      return null
    }
    headerError.value = extractErrorMessage(err)
    console.error('[ProblemsStore] Failed to fetch header:', err)
    return null
  } finally {
    headerLoading.value = false
  }
}

async function fetchDescription(id: string): Promise<DescriptionData | null> {
  const controller = getAbortController('description')
  descriptionLoading.value = true
  descriptionError.value = null

  try {
    const data = await problemsApi.getDescription(id, controller.signal)
    descriptionData.value = data
    return data
  } catch (err: unknown) {
    if ((err as Error).name === 'AbortError') {
      return null
    }
    descriptionError.value = extractErrorMessage(err)
    console.error('[ProblemsStore] Failed to fetch description:', err)
    return null
  } finally {
    descriptionLoading.value = false
  }
}

async function fetchCode(id: string): Promise<CodeData | null> {
  const controller = getAbortController('code')
  codeLoading.value = true
  codeError.value = null

  try {
    const data = await problemsApi.getCode(id, controller.signal)
    codeData.value = data
    return data
  } catch (err: unknown) {
    if ((err as Error).name === 'AbortError') {
      return null
    }
    codeError.value = extractErrorMessage(err)
    console.error('[ProblemsStore] Failed to fetch code:', err)
    return null
  } finally {
    codeLoading.value = false
  }
}

async function fetchCases(id: string): Promise<CasesData | null> {
  const controller = getAbortController('cases')
  casesLoading.value = true
  casesError.value = null

  try {
    const data = await problemsApi.getCases(id, controller.signal)
    casesData.value = data
    return data
  } catch (err: unknown) {
    if ((err as Error).name === 'AbortError') {
      return null
    }
    casesError.value = extractErrorMessage(err)
    console.error('[ProblemsStore] Failed to fetch cases:', err)
    return null
  } finally {
    casesLoading.value = false
  }
}
```

- [ ] **Step 3: Update clearCurrentProblem function**

Replace the existing `clearCurrentProblem` function:

```typescript
function clearCurrentProblem() {
  // Clear data
  headerData.value = null
  headerError.value = null
  descriptionData.value = null
  descriptionError.value = null
  codeData.value = null
  codeError.value = null
  casesData.value = null
  casesError.value = null

  // Clear legacy state if needed
  currentProblem.value = null
  loadedProblemId.value = null
}
```

- [ ] **Step 4: Update return statement**

Update the store's return statement to export new state and functions. **Remove** the old `fetchDescriptionData`, `fetchCodeData`, `fetchCasesData` exports:

```typescript
return {
  // State
  problems,
  total,
  loading,
  error,
  currentProblem,

  // New flat state
  headerData,
  headerLoading,
  headerError,
  descriptionData,
  descriptionLoading,
  descriptionError,
  codeData,
  codeLoading,
  codeError,
  casesData,
  casesLoading,
  casesError,

  // Actions
  fetchProblems,
  fetchProblem,
  fetchHeader,
  fetchDescription,
  fetchCode,
  fetchCases,
  createProblem,
  updateProblem,
  updateProblemWithPublish,
  deleteProblem,
  publishProblem,
  unpublishProblem,
  bulkAction,
  clearError,
  clearCurrentProblem,
  clearTabData: clearCurrentProblem, // Alias for backward compatibility
  abortAllRequests,
  reset,
}
```

- [ ] **Step 5: Verify TypeScript compiles**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add management/src/stores/admin/problems.ts
git commit -m "refactor(frontend): add AbortController and new fetch functions to problems store"
```

---

## Chunk 4: Frontend View Refactor

### Task 10: Refactor ProblemDetailView Script

**Files:**
- Modify: `management/src/views/problems/ProblemDetailView.vue`

- [ ] **Step 1: Update script imports and setup**

Replace the imports section:

```typescript
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ArrowLeft, Edit, Eye, EyeOff, FileText, History, RefreshCw } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import DescriptionDisplay from './components/DescriptionDisplay.vue'
import CodeDisplay from './components/CodeDisplay.vue'
import CasesDisplay from './components/CasesDisplay.vue'
import VersionHistoryTimeline from '@/components/problems/VersionHistoryTimeline.vue'
import AuditLogViewer from '@/components/audit/AuditLogViewer.vue'
```

- [ ] **Step 2: Replace reactive state and computed properties**

Replace the existing reactive state section with:

```typescript
const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const store = useProblemsStore()

const publishing = ref(false)
const versionHistoryOpen = ref(false)
const isLoaded = ref(false)

// Valid tab values
const VALID_TABS = ['description', 'code', 'cases', 'audit'] as const
type TabType = (typeof VALID_TABS)[number]

// Route params
const problemId = computed(() => route.params.id as string)
const currentTab = computed<TabType>(() => {
  const tab = route.params.tab as string
  if (VALID_TABS.includes(tab as TabType)) {
    return tab as TabType
  }
  return 'description'
})
```

- [ ] **Step 3: Add data loading functions**

Add after the computed properties:

```typescript
// Load page data (header + current tab in parallel)
async function loadPageData() {
  const id = problemId.value
  if (!id) return

  await Promise.all([
    store.fetchHeader(id),
    fetchTabData(currentTab.value),
  ])

  setTimeout(() => {
    isLoaded.value = true
  }, 100)
}

// Fetch data for specific tab
async function fetchTabData(tab: TabType) {
  const id = problemId.value
  if (!id) return

  switch (tab) {
    case 'description':
      await store.fetchDescription(id)
      break
    case 'code':
      await store.fetchCode(id)
      break
    case 'cases':
      await store.fetchCases(id)
      break
    case 'audit':
      // Audit tab loads its own data via AuditLogViewer component
      break
  }
}

// Retry functions
async function retryHeader() {
  await store.fetchHeader(problemId.value)
}

async function retryTab(tab: TabType) {
  await fetchTabData(tab)
}
```

- [ ] **Step 4: Update lifecycle hooks and watchers**

Replace the existing watch and lifecycle hooks:

```typescript
// Watch for tab changes
watch(
  currentTab,
  (newTab, oldTab) => {
    if (newTab !== oldTab) {
      fetchTabData(newTab)
    }
  },
  { immediate: false },
)

// Watch for problemId changes (navigating to different problem)
watch(
  problemId,
  (newId, oldId) => {
    if (newId && newId !== oldId) {
      isLoaded.value = false
      store.clearCurrentProblem()
      loadPageData()
    }
  },
  { immediate: false },
)

// Mount: load initial data
onMounted(() => {
  if (problemId.value) {
    loadPageData()
  }
})

// Unmount: cancel requests and clear data
onUnmounted(() => {
  store.abortAllRequests()
  store.clearCurrentProblem()
})
```

- [ ] **Step 5: Update publish and edit functions**

Replace the existing `togglePublish` and `editProblem` functions:

```typescript
async function togglePublish() {
  if (!store.headerData) return
  publishing.value = true
  try {
    if (store.headerData.is_published) {
      await store.unpublishProblem(problemId.value)
      toast.success(t('problems.toast.unpublishSuccess'))
    } else {
      await store.publishProblem(problemId.value)
      toast.success(t('problems.toast.publishSuccess'))
    }
    // Refresh header to get updated publish state
    await store.fetchHeader(problemId.value)
  } catch (error) {
    console.error('Failed to toggle publish:', error)
    toast.error(t('problems.toast.publishFailed'))
  } finally {
    publishing.value = false
  }
}

function editProblem() {
  router.push({
    name: 'problem-edit',
    params: { id: problemId.value, tab: currentTab.value },
  })
}

async function handleVersionRestored() {
  toast.success(t('problems.versionHistory.restoreSuccess'))
  await loadPageData()
}

function handleTabChange(value: string | number) {
  const tab = value as TabType
  router.push({
    name: 'problem-detail',
    params: { id: problemId.value, tab },
  })
}
```

- [ ] **Step 6: Commit**

```bash
git add management/src/views/problems/ProblemDetailView.vue
git commit -m "refactor(frontend): update ProblemDetailView script with new data loading logic"
```

---

### Task 11: Update ProblemDetailView Template

**Files:**
- Modify: `management/src/views/problems/ProblemDetailView.vue`

**Key changes:**
- Header uses `store.headerData` / `store.headerLoading` / `store.headerError`
- Description tab uses `store.descriptionData` / `store.descriptionLoading` / `store.descriptionError`
- Code tab uses `store.codeData` / `store.codeLoading` / `store.codeError`
- Cases tab uses `store.casesData` / `store.casesLoading` / `store.casesError`
- Each tab has loading skeleton, error state with retry button, and content display

- [ ] **Step 1: Update header section in template**

The header section should now reference `store.headerData`, `store.headerLoading`, `store.headerError` instead of the old `descriptionData` references.

- [ ] **Step 2: Add error UI with retry buttons**

Each tab section should have:
1. Loading skeleton
2. Error state with retry button
3. Content display

Example pattern for each tab:
```vue
<!-- Example: Description Tab -->
<template v-if="currentTab === 'description'">
  <div v-if="store.descriptionLoading" class="space-y-6">
    <!-- skeleton -->
  </div>
  <div
    v-else-if="store.descriptionError"
    class="flex items-center justify-between border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] dark:bg-[oklch(0.6_0.2_25/0.15)] p-4"
  >
    <div class="flex items-center gap-3">
      <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
      <span class="text-sm text-[var(--foreground)]">{{ store.descriptionError }}</span>
    </div>
    <Button
      variant="terminal"
      size="sm"
      class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)]"
      @click="retryTab('description')"
    >
      <RefreshCw :size="14" class="mr-1.5" />
      {{ t('common.retry') }}
    </Button>
  </div>
  <DescriptionDisplay
    v-else-if="store.descriptionData"
    :problem="store.descriptionData"
  />
</template>
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add management/src/views/problems/ProblemDetailView.vue
git commit -m "refactor(frontend): update ProblemDetailView template with new data structure"
```

---

## Chunk 5: Integration Testing

### Task 12: Run Full Quality Checks

- [ ] **Step 1: Run backend type check**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 2: Run frontend type check**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 3: Run frontend lint**

Run: `cd management && pnpm lint`
Expected: No errors (or auto-fix applied)

- [ ] **Step 4: Start backend and verify API endpoints**

Run: `cd backend && pnpm start:dev`

Test endpoints:
```bash
curl -X GET http://localhost:3000/admin/problems/1/header -H "Cookie: <auth-cookie>"
curl -X GET http://localhost:3000/admin/problems/1/description -H "Cookie: <auth-cookie>"
curl -X GET http://localhost:3000/admin/problems/1/code -H "Cookie: <auth-cookie>"
curl -X GET http://localhost:3000/admin/problems/1/cases -H "Cookie: <auth-cookie>"
```

Expected: All return valid JSON responses

- [ ] **Step 5: Start frontend and verify UI**

Run: `cd management && pnpm dev`

Verify:
1. Header loads correctly
2. Tab switching works
3. Error states display correctly
4. Retry buttons work
5. Fast tab switching doesn't cause race conditions

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "feat: complete problem detail data architecture refactor

- Add 4 lightweight backend API endpoints (header, description, code, cases)
- Flatten frontend store structure with independent loading/error states
- Implement AbortController for race condition handling
- Parallel load header + initial tab data on page entry
- Keep Display component props interface for reusability"
```

---

## Summary

| Chunk | Tasks | Files Modified |
|-------|-------|----------------|
| 1 | 1-5 | Backend: DTO + 4 API endpoints |
| 2 | 6-7 | Frontend: API layer + types |
| 3 | 8-9 | Frontend: Store refactor |
| 4 | 10-11 | Frontend: View refactor |
| 5 | 12 | Integration testing |
