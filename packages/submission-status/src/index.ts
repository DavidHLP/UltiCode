/**
 * submission-status — the single source of truth linking sandbox verdicts
 * to badge colors, presentation categories, and state classifiers.
 *
 * History: badge-config previously owned a SUBMISSION_STATUS_COLOR_MAP keyed
 * by UPPERCASE_UNDERSCORE while sandbox-types/DFormVerdict used 'Title Case
 * Space' values. They were unlinked — adding a verdict could silently render
 * a colorless badge. This module is now the sole owner of the verdict→color,
 * →category, →icon-key, and →i18n-key truth; the legacy map was removed once
 * all UI callers migrated here.
 *
 * Architecture review candidates #5 and #8 — shared-package contract test,
 * state classification, and centralization.
 */

import type { DFormVerdict } from '@ulticode/sandbox-types'
import type { SemanticColor } from '@ulticode/badge-config'

/**
 * Map a D-form verdict (Title Case) to the UPPERCASE_UNDERSCORE status key
 * used by the badge color map. Every DFormVerdict has an entry — if you
 * add a verdict to the union, TypeScript forces you to add it here too.
 */
export const VERDICT_TO_STATUS_KEY: Record<DFormVerdict, string> = {
  Accepted: 'ACCEPTED',
  'Wrong Answer': 'WRONG_ANSWER',
  'Time Limit Exceeded': 'TIME_LIMIT_EXCEEDED',
  'Memory Limit Exceeded': 'MEMORY_LIMIT_EXCEEDED',
  'Output Limit Exceeded': 'OUTPUT_LIMIT_EXCEEDED',
  'Runtime Error': 'RUNTIME_ERROR',
  'Compile Error': 'COMPILE_ERROR',
  'Presentation Error': 'PRESENTATION_ERROR',
  'System Error': 'SYSTEM_ERROR',
  'Sandbox Error': 'SANDBOX_ERROR',
  Judging: 'JUDGING',
  Pending: 'PENDING',
}

/**
 * State classification — every verdict settles into one of two states so
 * consumers can ask "is this still moving?" without re-implementing the
 * membership list at each surface. State and responsibility are kept on
 * separate axes (see VERDICT_IS_INFRA below) so a verdict can be both
 * settled and infrastructure-attributable.
 *
 *   - 'final'    — verdict will not change without a new submission
 *                  (Accepted, WA, TLE/MLE/OLE, Runtime/Compile/Presentation
 *                  Error, System Error, Sandbox Error)
 *   - 'pending'  — verdict may change as judging progresses (Judging, Pending)
 *
 * Every DFormVerdict has an entry — if you add a verdict to the union,
 * TypeScript forces you to classify it here too.
 */
export type VerdictState = 'final' | 'pending'

export const VERDICT_STATE: Record<DFormVerdict, VerdictState> = {
  Accepted: 'final',
  'Wrong Answer': 'final',
  'Time Limit Exceeded': 'final',
  'Memory Limit Exceeded': 'final',
  'Output Limit Exceeded': 'final',
  'Runtime Error': 'final',
  'Compile Error': 'final',
  'Presentation Error': 'final',
  'System Error': 'final',
  'Sandbox Error': 'final',
  Judging: 'pending',
  Pending: 'pending',
}

/**
 * Responsibility classification — true when the verdict indicates a sandbox /
 * platform failure rather than a user-attributable error. Surfaces can use
 * this to render an "infrastructure issue" badge without polluting
 * user-attributable error styling. Every DFormVerdict has an entry — if you
 * add a verdict to the union, TypeScript forces you to classify it here too.
 */
export const VERDICT_IS_INFRA: Record<DFormVerdict, boolean> = {
  Accepted: false,
  'Wrong Answer': false,
  'Time Limit Exceeded': false,
  'Memory Limit Exceeded': false,
  'Output Limit Exceeded': false,
  'Runtime Error': false,
  'Compile Error': false,
  'Presentation Error': false,
  'System Error': true,
  'Sandbox Error': true,
  Judging: false,
  Pending: false,
}

/**
 * Stable icon-key per verdict. Returns a small framework-agnostic token that
 * each surface maps to its own icon set (e.g. lucide-vue-next). Centralizing
 * the verdict→icon mapping here stops every surface from hand-rolling a
 * 12-arm switch that can silently drift (e.g. collapsing every non-Accepted
 * verdict to a single "Clock" icon — the historical bug).
 */
export type VerdictIconKey =
  | 'success'
  | 'error'
  | 'warning'
  | 'pending'
  | 'neutral'

export const VERDICT_ICON_KEY: Record<DFormVerdict, VerdictIconKey> = {
  Accepted: 'success',
  'Wrong Answer': 'error',
  'Time Limit Exceeded': 'error',
  'Memory Limit Exceeded': 'error',
  'Output Limit Exceeded': 'error',
  'Runtime Error': 'error',
  'Compile Error': 'error',
  'Presentation Error': 'warning',
  'System Error': 'neutral',
  'Sandbox Error': 'neutral',
  Judging: 'pending',
  Pending: 'pending',
}

/**
 * Get the icon-key for a verdict. Falls back to 'neutral' for forward
 * compatibility with verdicts that have not been classified yet.
 */
export function getVerdictIconKey(verdict: DFormVerdict): VerdictIconKey {
  return VERDICT_ICON_KEY[verdict] ?? 'neutral'
}

/**
 * Map a D-form verdict (Title Case) to its full i18n key path
 * (`submission.status.<camelCase>`). Every DFormVerdict has an entry — if you
 * add a verdict to the union, TypeScript forces you to add it here too.
 *
 * The label text itself stays in each application's i18n bundles; this map is
 * only the single source of truth for WHICH verdicts exist and what their
 * canonical key path is, so surfaces never again hand-roll an incomplete copy
 * that omits a status (the historical "Sandbox Error renders raw English"
 * regression came from exactly such duplicated, fallback-less maps).
 */
export const VERDICT_TO_LABEL_I18N_KEY: Record<DFormVerdict, string> = {
  Accepted: 'submission.status.accepted',
  'Wrong Answer': 'submission.status.wrongAnswer',
  'Time Limit Exceeded': 'submission.status.timeLimitExceeded',
  'Memory Limit Exceeded': 'submission.status.memoryLimitExceeded',
  'Output Limit Exceeded': 'submission.status.outputLimitExceeded',
  'Runtime Error': 'submission.status.runtimeError',
  'Compile Error': 'submission.status.compileError',
  'Presentation Error': 'submission.status.presentationError',
  'System Error': 'submission.status.systemError',
  'Sandbox Error': 'submission.status.sandboxError',
  Judging: 'submission.status.judging',
  Pending: 'submission.status.pending',
}

export const VERDICT_COLOR_MAP: Record<DFormVerdict, SemanticColor> = {
  Accepted: 'success',
  'Wrong Answer': 'error',
  'Time Limit Exceeded': 'error',
  'Memory Limit Exceeded': 'error',
  'Output Limit Exceeded': 'error',
  'Runtime Error': 'error',
  'Compile Error': 'error',
  'Presentation Error': 'warning',
  'System Error': 'neutral',
  'Sandbox Error': 'neutral',
  Judging: 'warning',
  Pending: 'warning',
}

/**
 * Get the badge color for a verdict. Falls back to 'neutral' for
 * forward-compatibility if a new verdict is added before its color entry.
 */
export function getVerdictColor(verdict: DFormVerdict): SemanticColor {
  return VERDICT_COLOR_MAP[verdict] ?? 'neutral'
}

/**
 * True when the verdict will not change without a new submission.
 * Composed from VERDICT_STATE so the membership list lives in one place.
 * Note: a verdict can be both final AND infrastructure-attributable (see
 * `isInfra`) — the two axes are independent.
 */
export function isFinal(verdict: DFormVerdict): boolean {
  return VERDICT_STATE[verdict] === 'final'
}

/**
 * True when the verdict may change as judging progresses (Judging, Pending).
 */
export function isPending(verdict: DFormVerdict): boolean {
  return VERDICT_STATE[verdict] === 'pending'
}

/**
 * True when the verdict indicates a sandbox / platform failure — not the
 * user's fault. Surfaces can use this to render an "infrastructure issue"
 * badge without polluting user-attributable error styling.
 */
export function isInfra(verdict: DFormVerdict): boolean {
  return VERDICT_IS_INFRA[verdict] === true
}

/**
 * Get the UPPERCASE status key for a verdict — the key to use when
 * looking up colors in the legacy SUBMISSION_STATUS_COLOR_MAP.
 */
export function verdictToStatusKey(verdict: DFormVerdict): string {
  return VERDICT_TO_STATUS_KEY[verdict]
}

/**
 * Reverse lookup — UPPERCASE status key → verdict, derived from
 * VERDICT_TO_STATUS_KEY so there is a single source of truth.
 */
const STATUS_KEY_TO_VERDICT = new Map<string, DFormVerdict>(
  (Object.entries(VERDICT_TO_STATUS_KEY) as [DFormVerdict, string][]).map(
    ([verdict, key]) => [key, verdict],
  ),
)

/**
 * Normalize a submission status string (any casing) to the UPPERCASE_UNDERSCORE
 * status key. Backend status values arrive in mixed casings ("Accepted",
 * "WRONG ANSWER", "wrong_answer"); this yields one canonical key for both color
 * lookup and i18n labels.
 */
export function normalizeStatusKey(status: string): string {
  return status.toUpperCase().replace(/\s+/g, '_')
}

/**
 * UI-facing entry point for the verdict→color truth. Accepts an untyped
 * status string (backend payloads arrive in mixed casings and shapes) and
 * resolves it to a SemanticColor via the same single source of truth that
 * `getVerdictColor` uses. Unknown statuses fall back to 'neutral'.
 *
 * The `getStatusColor` / `getVerdictColor` duality is intentional:
 * `getVerdictColor` is the typed, compile-time-safe entry; `getStatusColor`
 * is the untyped boundary that absorbs whatever the backend sends. They
 * share the same verdict→color map; do not re-implement either side.
 */
export function getStatusColor(status: string): SemanticColor {
  const verdict = STATUS_KEY_TO_VERDICT.get(normalizeStatusKey(status))
  return verdict ? getVerdictColor(verdict) : 'neutral'
}

/**
 * UI-facing entry point for verdict→icon-key. Accepts an untyped status
 * string (mixed casing); unknown statuses fall back to 'neutral'. Mirrors
 * the `getStatusColor` / `getVerdictIconKey` duality for the same boundary
 * reason — backends send strings, surfaces need a framework-agnostic key.
 */
export function getStatusIconKey(status: string): VerdictIconKey {
  const verdict = STATUS_KEY_TO_VERDICT.get(normalizeStatusKey(status))
  return verdict ? getVerdictIconKey(verdict) : 'neutral'
}

/**
 * UI-facing entry point for verdict state. Accepts an untyped status
 * string (mixed casing); unknown statuses are conservatively classified as
 * 'final' so callers default to displaying a settled verdict rather than
 * a perpetual pending spinner.
 */
export function getStatusState(status: string): VerdictState {
  const verdict = STATUS_KEY_TO_VERDICT.get(normalizeStatusKey(status))
  return verdict ? VERDICT_STATE[verdict] : 'final'
}

/**
 * Reverse lookup — UPPERCASE status key → i18n label key, derived from
 * VERDICT_TO_LABEL_I18N_KEY so the label map stays the single source of truth.
 */
const STATUS_KEY_TO_LABEL_I18N_KEY = new Map<string, string>(
  (Object.entries(VERDICT_TO_LABEL_I18N_KEY) as [DFormVerdict, string][]).map(
    ([verdict, labelKey]) => [normalizeStatusKey(verdict), labelKey],
  ),
)

/**
 * Resolve a submission status string (any casing) to its full i18n label key
 * path, or `null` when the status is not a known verdict. Returning `null`
 * (rather than throwing) lets the caller preserve its prior fallback for
 * forward-compatibility with not-yet-mapped statuses — typically
 * `key ? t(key) : status`. This replaces the per-surface status→label maps
 * that previously omitted statuses such as 'Sandbox Error'.
 */
export function getStatusLabelI18nKey(status: string): string | null {
  return STATUS_KEY_TO_LABEL_I18N_KEY.get(normalizeStatusKey(status)) ?? null
}
