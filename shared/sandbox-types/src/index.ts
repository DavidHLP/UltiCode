/**
 * @ulticode/sandbox-types — shared TypeScript types for the D-form
 * (LeetCode/HackerRank) sandbox contract.
 *
 * <p>These mirror the Java records and Python dataclasses the harness
 * emits. Frontends import from {@code @/shared/sandbox-types/src/...}
 * (resolved via the {@code shared} symlink in each frontend) so the
 * submission flow stays aligned with the backend's DTOs.
 *
 * <p>Plan reference: {@code .claude/PRPs/plans/oj-sandbox-d-form-refactor.plan.md}
 * Phase 4 — shared DTO alignment.
 */

export type {
  DFormVerdict,
  DFormHarnessVerdict,
  DFormVerdictCategory,
} from './verdict.js';

export type { OJDataType } from './oj-type.js';
export { SUPPORTED_OJ_DATA_TYPES, isSupportedOJDataType } from './oj-type.js';

export type { DFormInputSpec } from './input-spec.js';
export { isDFormInputSpec } from './input-spec.js';

export type { DFormPerCaseResult, DFormCaseError } from './per-case.js';
export { verdictCategory } from './per-case.js';

export type { DFormEnvelope } from './envelope.js';
