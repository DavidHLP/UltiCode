import { ref, computed, readonly } from 'vue'
import {
  contestsApi,
  ContestType,
  type Contest,
  type CreateContestDto,
  type AddContestProblemDto,
} from '@/api/admin/contests'

/**
 * Per-problem entry carried inside a `ContestDraft`. The numeric `id` matches
 * the backend's `Long problemId`; it is parsed via `Number(...)` at the
 * persistence boundary.
 */
export interface ContestDraftProblem {
  id: string
  slug: string
  title: string
  difficulty: string
  score: number
}

/**
 * Authoring draft owned by `useContestAuthoring`. The shape is the single
 * source of truth for every wizard step: each step receives a typed slice of
 * this draft and emits typed patches that flow back through composable
 * actions. `startTimeLocal` keeps the browser-local `datetime-local` string
 * (`YYYY-MM-DDTHH:MM`); conversion to ISO 8601 UTC happens at the persistence
 * boundary in `buildCreatePayload`.
 */
export interface ContestDraft {
  title: string
  slug: string
  description: string
  contestType: ContestType
  scoringRuleId: string
  startTimeLocal: string
  duration: number
  isPublished: boolean
  problems: ContestDraftProblem[]
}

/** Default per-problem score injected when a new problem enters the draft. */
export const DEFAULT_PROBLEM_SCORE = 100
/** Default contest duration in minutes when a fresh draft is created. */
export const DEFAULT_DURATION_MINUTES = 120

// ---------------------------------------------------------------------------
// Per-step slice types. Slices are the only window a step gets into the
// draft; they are intentionally narrow so steps cannot silently depend on
// unrelated fields.
// ---------------------------------------------------------------------------

export interface BasicInfoSlice {
  title: string
  slug: string
  description: string
  contestType: ContestType
}
export type BasicInfoPatch = Partial<BasicInfoSlice>

export interface ScoringRuleSlice {
  scoringRuleId: string
}
export type ScoringRulePatch = Pick<ScoringRuleSlice, 'scoringRuleId'>

export interface ScheduleSlice {
  startTimeLocal: string
  duration: number
  isPublished: boolean
}
export type SchedulePatch = Partial<ScheduleSlice>

export interface ProblemsSlice {
  problems: ContestDraftProblem[]
}
export type ProblemsPatch = Pick<ProblemsSlice, 'problems'>

/**
 * Review reads the whole draft so the summary screen can render every field
 * the user is about to commit. The slice is a shallow copy so steps cannot
 * mutate the source draft through it.
 */
export type ReviewSlice = ContestDraft

function createInitialDraft(): ContestDraft {
  return {
    title: '',
    slug: '',
    description: '',
    contestType: ContestType.ICPC,
    scoringRuleId: '',
    startTimeLocal: '',
    duration: DEFAULT_DURATION_MINUTES,
    isPublished: false,
    problems: [],
  }
}

/**
 * Convert a browser-local `datetime-local` string (`YYYY-MM-DDTHH:MM`) into an
 * ISO 8601 UTC string suitable for the backend's `LocalDateTime` binding.
 * Returns `null` when the input is empty or does not parse to a real instant.
 */
function toISO8601(datetimeLocal: string): string | null {
  if (!datetimeLocal) return null
  const date = new Date(datetimeLocal)
  if (Number.isNaN(date.getTime())) return null
  return date.toISOString()
}

/**
 * Deep module that owns the contest-authoring workspace: draft state, per-step
 * invariants, typed actions, persistence shaping, and submit orchestration.
 * The shell (`ContestWizard.vue`) destructures the returned slices and
 * actions; step components become thin adapters that emit typed patches.
 */
export function useContestAuthoring() {
  const draft = ref<ContestDraft>(createInitialDraft())
  const submitting = ref(false)
  const submitError = ref<string | null>(null)

  // ----- Per-step typed read-only slices ------------------------------------
  const basicInfoSlice = computed<BasicInfoSlice>(() => ({
    title: draft.value.title,
    slug: draft.value.slug,
    description: draft.value.description,
    contestType: draft.value.contestType,
  }))

  const scoringRuleSlice = computed<ScoringRuleSlice>(() => ({
    scoringRuleId: draft.value.scoringRuleId,
  }))

  const scheduleSlice = computed<ScheduleSlice>(() => ({
    startTimeLocal: draft.value.startTimeLocal,
    duration: draft.value.duration,
    isPublished: draft.value.isPublished,
  }))

  const problemsSlice = computed<ProblemsSlice>(() => ({
    problems: draft.value.problems,
  }))

  const reviewSlice = computed<ReviewSlice>(() => ({ ...draft.value }))

  // ----- Per-step invariant validators --------------------------------------
  // Replaces the parent's `switch (currentStep)` validation block. Each
  // validator owns one step's invariants and is independently testable.
  const basicInfoValid = computed<boolean>(() => draft.value.title.trim().length > 0)

  // Scoring rule is optional; the selector auto-picks a default on mount, and
  // even an empty value is acceptable to the backend (which falls back to a
  // built-in default rule).
  const scoringRuleValid = computed<boolean>(() => true)

  const scheduleValid = computed<boolean>(() => {
    const { startTimeLocal, duration } = draft.value
    if (!startTimeLocal) return false
    if (toISO8601(startTimeLocal) === null) return false
    return Number.isFinite(duration) && duration > 0
  })

  // Problems are optional; a contest can be created without any.
  const problemsValid = computed<boolean>(() => true)

  // Final review step is valid exactly when every upstream invariant holds.
  const reviewValid = computed<boolean>(
    () =>
      basicInfoValid.value && scoringRuleValid.value && scheduleValid.value && problemsValid.value,
  )

  const canSubmit = computed<boolean>(() => reviewValid.value)

  // ----- Actions ------------------------------------------------------------
  function patchBasicInfo(patch: BasicInfoPatch): void {
    if (patch.title !== undefined) draft.value.title = patch.title
    if (patch.slug !== undefined) draft.value.slug = patch.slug
    if (patch.description !== undefined) draft.value.description = patch.description
    if (patch.contestType !== undefined) draft.value.contestType = patch.contestType
  }

  function setScoringRuleId(value: string): void {
    draft.value.scoringRuleId = value
  }

  function patchSchedule(patch: SchedulePatch): void {
    if (patch.startTimeLocal !== undefined) draft.value.startTimeLocal = patch.startTimeLocal
    if (patch.duration !== undefined) draft.value.duration = patch.duration
    if (patch.isPublished !== undefined) draft.value.isPublished = patch.isPublished
  }

  /**
   * Add a problem selected from `ContestProblemPicker`. The picker emits
   * `{ id, title, slug, difficulty }` without a score; this action injects
   * the default score so the persistence layer always receives a concrete
   * value. Duplicates (by id) are ignored, preserving the picker's
   * exclude-ids semantics.
   */
  function addProblem(problem: {
    id: string
    title: string
    slug: string
    difficulty: string
  }): void {
    if (draft.value.problems.some((p) => p.id === problem.id)) return
    draft.value.problems = [...draft.value.problems, { ...problem, score: DEFAULT_PROBLEM_SCORE }]
  }

  function removeProblem(problemId: string): void {
    draft.value.problems = draft.value.problems.filter((p) => p.id !== problemId)
  }

  function setProblemScore(problemId: string, score: number): void {
    draft.value.problems = draft.value.problems.map((p) =>
      p.id === problemId ? { ...p, score } : p,
    )
  }

  function reset(): void {
    draft.value = createInitialDraft()
    submitting.value = false
    submitError.value = null
  }

  // ----- Persistence shaping ------------------------------------------------
  /**
   * Build the `CreateContestDto` payload for the current draft. `slug` is
   * forwarded to the backend so it can be persisted when provided. `problemIds`
   * is intentionally omitted: per-problem scores flow through
   * `buildProblemScorePatches` after the contest exists. Sending `problemIds`
   * here would cause the backend to bulk-insert each problem with `score = 0`,
   * and the subsequent scored
   * `addProblem` calls would 400 with "Problem already exists in this
   * contest".
   */
  function buildCreatePayload(): CreateContestDto {
    const startTime = toISO8601(draft.value.startTimeLocal)
    if (startTime === null) {
      throw new Error('Invalid start time')
    }
    const trimmedDescription = draft.value.description.trim()
    const scoringRuleId = draft.value.scoringRuleId.trim()
    return {
      title: draft.value.title.trim(),
      slug: draft.value.slug.trim(),
      description: trimmedDescription === '' ? undefined : trimmedDescription,
      contestType: draft.value.contestType,
      startTime,
      duration: draft.value.duration,
      isPublished: draft.value.isPublished,
      scoringRuleId: scoringRuleId === '' ? undefined : scoringRuleId,
    }
  }

  /**
   * Build the per-problem scored `addProblem` payloads. Each entry pairs the
   * numeric problem id with the author's chosen score so the wizard can issue
   * one scored `POST /admin/contest/{id}/problems` per problem after the
   * contest is created.
   */
  function buildProblemScorePatches(): AddContestProblemDto[] {
    return draft.value.problems.map((p) => ({
      problemId: Number(p.id),
      score: p.score,
    }))
  }

  /**
   * Orchestrate the full submission: create the contest, then issue one
   * scored `addProblem` call per drafted problem. The order is sequential so
   * a partial failure surfaces the first error to the caller; problems added
   * before the failure remain persisted (no in-place rollback). The draft
   * is reset only by the caller after a successful submit.
   */
  async function submit(): Promise<Contest> {
    submitting.value = true
    submitError.value = null
    try {
      const payload = buildCreatePayload()
      const contest = await contestsApi.createContest(payload)
      const patches = buildProblemScorePatches()
      for (const patch of patches) {
        await contestsApi.addProblem(contest.id, patch)
      }
      return contest
    } catch (err: unknown) {
      submitError.value = err instanceof Error ? err.message : 'Submission failed'
      throw err
    } finally {
      submitting.value = false
    }
  }

  return {
    // state (read-only to consumers)
    draft: readonly(draft),
    submitting: readonly(submitting),
    submitError: readonly(submitError),
    // typed slices
    basicInfoSlice,
    scoringRuleSlice,
    scheduleSlice,
    problemsSlice,
    reviewSlice,
    // per-step invariant validators
    basicInfoValid,
    scoringRuleValid,
    scheduleValid,
    problemsValid,
    reviewValid,
    canSubmit,
    // actions
    patchBasicInfo,
    setScoringRuleId,
    patchSchedule,
    addProblem,
    removeProblem,
    setProblemScore,
    reset,
    // persistence shaping + orchestration
    buildCreatePayload,
    buildProblemScorePatches,
    submit,
  }
}
