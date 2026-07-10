/**
 * submission-status — the single source of truth linking sandbox verdicts
 * to badge colors.
 *
 * History: badge-config previously owned a SUBMISSION_STATUS_COLOR_MAP keyed
 * by UPPERCASE_UNDERSCORE while sandbox-types/DFormVerdict used 'Title Case
 * Space' values. They were unlinked — adding a verdict could silently render
 * a colorless badge. This module is now the sole owner of the verdict→color
 * truth; the legacy map was removed once all UI callers migrated here.
 *
 * Architecture review candidate #8 — shared-package contract test.
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
 * Complete color map keyed by verdict — covers every DFormVerdict value.
 *
 * The original SUBMISSION_STATUS_COLOR_MAP was missing four verdicts
 * (Output Limit Exceeded, Presentation Error, System Error, Sandbox Error).
 * This map fills those gaps so no verdict ever renders without a color.
 */
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
 * Get the badge color for a submission status string in any casing. This is the
 * UI-facing entry point — it replaces the legacy per-app
 * SUBMISSION_STATUS_COLOR_MAP[normalized] lookups. Covers all 12 verdicts;
 * unknown statuses fall back to 'neutral'.
 */
export function getStatusColor(status: string): SemanticColor {
  const verdict = STATUS_KEY_TO_VERDICT.get(normalizeStatusKey(status))
  return verdict ? getVerdictColor(verdict) : 'neutral'
}
