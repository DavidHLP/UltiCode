/**
 * submission-status — the single source of truth linking sandbox verdicts
 * to badge colors.
 *
 * Before this module existed, {@code shared/badge-config/SUBMISSION_STATUS_COLOR_MAP}
 * used UPPERCASE_UNDERSCORE keys and {@code shared/sandbox-types/DFormVerdict}
 * used 'Title Case Space' values. They were unlinked: adding a new verdict
 * could silently produce a colorless badge. This module closes that seam.
 *
 * Architecture review candidate #8 — shared-package contract test.
 */

import type { DFormVerdict } from '@ulticode/sandbox-types'
import { SUBMISSION_STATUS_COLOR_MAP } from '@ulticode/badge-config'
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

// Re-export for consumers that want the original color map alongside the
// verdict-keyed version.
export { SUBMISSION_STATUS_COLOR_MAP }
