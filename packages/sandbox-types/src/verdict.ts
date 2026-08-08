/**
 * Verdict string literals that the D-form harness can emit AND the legacy
 * Form A wrapper can produce. Backend (CodeExecutionHelperImpl) maps both
 * representations into this same set so the frontends never have to
 * distinguish.
 *
 * <p>Spelling matches {@code docker/sandbox/harness/{java,python}/} stdout
 * verbatim, plus the legacy Form A spellings the backend has been
 * surfacing to the API for years.
 */
export type DFormVerdict =
  | 'Accepted'
  | 'Wrong Answer'
  | 'Time Limit Exceeded'
  | 'Memory Limit Exceeded'
  | 'Output Limit Exceeded'
  | 'Runtime Error'
  | 'Compile Error'
  | 'Presentation Error'
  | 'System Error'
  | 'Sandbox Error'  // Form A: daemon-level fork failure (e.g. pids-limit)
  | 'Judging'
  | 'Pending';

/** Narrow D-form verdict — only the verdicts the D-form harness can produce. */
export type DFormHarnessVerdict =
  | 'Accepted'
  | 'Wrong Answer'
  | 'Runtime Error'
  | 'Time Limit Exceeded'
  | 'Compile Error';

/** Coarse verdict category for UI styling. */
export type DFormVerdictCategory =
  | 'success'   // Accepted
  | 'error'     // Runtime Error, Compile Error, Wrong Answer, Presentation Error
  | 'warning'   // Time Limit Exceeded, Memory Limit Exceeded, Output Limit Exceeded
  | 'pending'   // Judging, Pending
  | 'system';   // System Error, Sandbox Error
