/**
 * Re-export the shared D-form sandbox types from {@code @ulticode/sandbox-types}
 * (resolved via the {@code shared} symlink in this frontend's src/).
 *
 * <p>The single source of truth lives in {@code 共享/sandbox-types/src/}
 * and mirrors the backend's {@code EnvelopeDTO} / {@code PerCaseResultDTO} /
 * {@code InputSpecDTO} records (Phase 3). Frontends consume this barrel
 * to stay aligned with the backend contract.
 *
 * <p>Do not add per-frontend extensions here. If a frontend needs a
 * derived type, define it locally and reference the shared fields.
 */

export type {
  DFormVerdict,
  DFormHarnessVerdict,
  DFormVerdictCategory,
  OJDataType,
  DFormInputSpec,
  DFormPerCaseResult,
  DFormCaseError,
  DFormEnvelope,
} from "@/shared/sandbox-types/src/index";

export {
  SUPPORTED_OJ_DATA_TYPES,
  isSupportedOJDataType,
  isDFormInputSpec,
  verdictCategory,
} from "@/shared/sandbox-types/src/index";
