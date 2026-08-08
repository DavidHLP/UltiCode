import { describe, expect, it } from "vitest";
import {
  getCaseOutput,
  hasDisplayValue,
  hasResultDetails,
} from "./testResultDisplay";
import type { ProblemCaseResultDetail } from "@/types/test-results";

const createResult = (
  overrides: Partial<ProblemCaseResultDetail> = {},
): ProblemCaseResultDetail => ({
  id: "case-1",
  runId: "run-1",
  submissionTestId: "submission-test-1",
  testCaseId: "test-case-1",
  caseLabel: "Case 1",
  status: "Accepted",
  runtime: "0 ms",
  memory: "0 MB",
  ...overrides,
});

describe("test result display helpers", () => {
  it("treats empty and whitespace-only values as missing", () => {
    expect(hasDisplayValue(undefined)).toBe(false);
    expect(hasDisplayValue(null)).toBe(false);
    expect(hasDisplayValue("")).toBe(false);
    expect(hasDisplayValue("   ")).toBe(false);
    expect(hasDisplayValue("0")).toBe(true);
  });

  it("uses output before the legacy detail field", () => {
    expect(
      getCaseOutput(createResult({ output: "42", detail: "legacy" })),
    ).toBe("42");
    expect(getCaseOutput(createResult({ detail: "legacy" }))).toBe("legacy");
  });

  it("detects whether a case has any renderable detail", () => {
    expect(hasResultDetails(createResult())).toBe(false);
    expect(
      hasResultDetails(
        createResult({
          inputs: [
            {
              id: "input-1",
              label: "nums",
              name: "nums",
              value: "[]",
            },
          ],
        }),
      ),
    ).toBe(true);
    expect(hasResultDetails(createResult({ expectedOutput: "[]" }))).toBe(true);
  });
});
