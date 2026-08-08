import type { DFormPerCaseResult } from './per-case.js';

/**
 * Top-level envelope the D-form harness writes to stdout.
 *
 * <pre>{@code
 * {
 *   "harness_version": "1.0",
 *   "language": "java",
 *   "exit_code": 0,            // 0 = envelope well-formed; 2 = harness panic
 *   "total_elapsed_ms": 42,
 *   "results": [PerCaseResult, ...]
 * }
 * }</pre>
 *
 * <p>{@code exit_code == 0} ⇒ per-case verdicts are trustworthy.
 * {@code exit_code == 2} ⇒ the harness itself panicked (e.g. javac
 * failure on user code, ambiguous Solution method, parse error on
 * input.json). The backend should fall back to a system-level Runtime
 * Error in that case.
 */
export interface DFormEnvelope {
  harness_version: string;
  language: string;
  exit_code: number;
  total_elapsed_ms: number;
  results: DFormPerCaseResult[];
}
