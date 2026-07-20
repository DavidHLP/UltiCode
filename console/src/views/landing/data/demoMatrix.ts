/**
 * Static demo model for the judge-matrix chapter. Deliberately local and
 * typed: an illustrative sequence of test cells, never wired to any API and
 * never presented as live platform data (see the disclaimer rendered beside
 * it in MatrixSection).
 */

export interface DemoTestCell {
  id: string;
  status: "passed";
  timeMs: number;
  memoryMb: number;
}

export const DEMO_TEST_CELLS: readonly DemoTestCell[] = [
  { id: "case-01", status: "passed", timeMs: 42, memoryMb: 8.1 },
  { id: "case-02", status: "passed", timeMs: 38, memoryMb: 8.0 },
  { id: "case-03", status: "passed", timeMs: 51, memoryMb: 8.4 },
  { id: "case-04", status: "passed", timeMs: 47, memoryMb: 8.2 },
  { id: "case-05", status: "passed", timeMs: 55, memoryMb: 8.6 },
  { id: "case-06", status: "passed", timeMs: 44, memoryMb: 8.3 },
] as const;

/** Two-sum snippet shown in the parse chapter's editor fragment. */
export const DEMO_CODE_SNIPPET = `def two_sum(nums, target):
    seen = {}
    for i, x in enumerate(nums):
        if target - x in seen:
            return [seen[target - x], i]
        seen[x] = i`;
