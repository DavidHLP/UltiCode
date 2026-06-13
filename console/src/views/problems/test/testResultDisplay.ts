import type { ProblemCaseResultDetail } from "@/types/test-results";

export const hasDisplayValue = (
  value: string | null | undefined,
): value is string => typeof value === "string" && value.trim().length > 0;

export const getCaseOutput = (result: ProblemCaseResultDetail): string =>
  result.output ?? result.detail ?? "";

export const hasResultDetails = (result: ProblemCaseResultDetail): boolean =>
  Boolean(result.inputs?.length) ||
  hasDisplayValue(getCaseOutput(result)) ||
  hasDisplayValue(result.expectedOutput);
