import type { DFormHarnessVerdict } from './verdict.js';

/**
 * Per-case verdict block as written to the {@code results[]} array of
 * the D-form envelope. Mirrors the JSON shape produced by
 * {@code Main.runCase} (Java) and {@code main._run_case} (Python).
 */
export interface DFormPerCaseResult {
  case_id: string;
  label: string;
  elapsed_ms: number;
  /**
   * D-form verdict for this case. Frontends may receive legacy Form A
   * spellings too (see {@code DFormVerdict} for the full set); the
   * backend maps both into this single field.
   */
  status: DFormHarnessVerdict;
  /** The user's return value, JSON-serialized. May be `null`. */
  result: unknown;
  /**
   * Set when the harness's per-case timeout fired (Thread.interrupt on the
   * worker thread). Frontends can surface a "soft TLE" warning to users.
   */
  interrupted?: boolean;
  error?: DFormCaseError;
  user_stdout?: string;
  user_stderr?: string;
}

export interface DFormCaseError {
  type: string;   // e.g. "java.lang.NullPointerException"
  message: string;
  /**
   * Frame lines. Harness already strips Main$ / Harness$ / java.* /
   * jdk.* / sun.* frames; only user frames (Solution.*, helper
   * classes) survive. The frontend can render these verbatim or
   * further trim.
   */
  stack: string[];
}

/**
 * Coarse verdict → category mapping for the UI. Centralized so the two
 * frontends don't drift.
 */
export function verdictCategory(
  status: DFormHarnessVerdict | string,
): 'success' | 'error' | 'warning' | 'pending' | 'system' {
  switch (status) {
    case 'Accepted':
      return 'success';
    case 'Runtime Error':
    case 'Compile Error':
    case 'Wrong Answer':
    case 'Presentation Error':
      return 'error';
    case 'Time Limit Exceeded':
    case 'Memory Limit Exceeded':
    case 'Output Limit Exceeded':
      return 'warning';
    case 'Judging':
    case 'Pending':
      return 'pending';
    case 'System Error':
    case 'Sandbox Error':
    default:
      return 'system';
  }
}
