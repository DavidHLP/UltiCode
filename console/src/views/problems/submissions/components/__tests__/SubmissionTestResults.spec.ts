/**
 * ADR-001 / task #10 (P0-1) — console-side 0-leak invariant.
 *
 * The console (user-facing) SubmissionTestResults component and its
 * SubmissionRecord type MUST NOT contain hidden case data. The backend
 * {@code SubmissionServiceImpl.toVO()} strips HIDDEN rows via
 * {@code CaseScope.isUserVisible(scope)} before responding, and the
 * frontend type is a structural witness of that contract.
 *
 * If this test ever fails, the visibility contract has been broken —
 * review P0-1 backend projection, do not relax the type.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { fileURLToPath } from "url";
import { dirname, join } from "path";
import type {
  SubmissionRecord,
  SubmissionTestRecord,
} from "@/types/submission";

describe("console — SubmissionRecord 0-leak invariant (task #10 ADR-001)", () => {
  // Type-level witnesses: if SubmissionRecord / SubmissionTestRecord ever gain a
  // hidden-case field, these assignments fail to COMPILE — the real protection.
  // (Backend strips HIDDEN rows via CaseScope.isUserVisible; the console type is
  // a structural witness of that contract.)
  //
  // The earlier `expect(obj.isHidden).toBeUndefined()` form was theatre — it
  // asserted a hand-built literal, so adding the field to the type still passed.
  // Replaced with compile-time `keyof` conditionals. (reviewer P1-2 follow-up.)
  // Backend `HiddenCaseLeakIT` (asserts real Jackson JSON) remains source-of-truth.

  it("SubmissionRecord type forbids hidden-case fields (compile-time witness)", () => {
    type K = keyof SubmissionRecord;
    const noIsHidden: "isHidden" extends K ? never : true = true;
    const noIsHiddenSnake: "is_hidden" extends K ? never : true = true;
    const noCaseScope: "caseScope" extends K ? never : true = true;
    // runtime sanity (also keeps the bindings used so they aren't dropped)
    expect([noIsHidden, noIsHiddenSnake, noCaseScope]).toEqual([
      true,
      true,
      true,
    ]);
  });

  it("SubmissionTestRecord (per-test row) type forbids hidden-case fields (compile-time witness)", () => {
    type K = keyof SubmissionTestRecord;
    const noIsHidden: "isHidden" extends K ? never : true = true;
    const noIsHiddenSnake: "is_hidden" extends K ? never : true = true;
    const noCaseScope: "caseScope" extends K ? never : true = true;
    const noCaseScopeSnake: "case_scope" extends K ? never : true = true;
    const noInput: "input" extends K ? never : true = true;
    const noOutput: "output" extends K ? never : true = true;
    const noExpectedOutput: "expectedOutput" extends K ? never : true = true;
    expect([
      noIsHidden,
      noIsHiddenSnake,
      noCaseScope,
      noCaseScopeSnake,
      noInput,
      noOutput,
      noExpectedOutput,
    ]).toEqual([true, true, true, true, true, true, true]);
  });

  it("SubmissionRecord carries first-failed detail fields at top level only (not per-test)", () => {
    // This literal is also a type check: assigning it to SubmissionRecord verifies
    // input/output/expected_output ARE permitted at top level (excess-property
    // check); `tests` is intentionally absent (per-test rows never nest here).
    const sub: SubmissionRecord = {
      id: "sub-1",
      problem_id: 1,
      status: "Wrong Answer",
      language: "cpp",
      runtime: 100,
      memory: 50,
      input: "stdin payload",
      output: "actual output",
      expected_output: "expected output",
      created_at: "2026-06-14T00:00:00Z",
    };
    expect(sub.input).toBe("stdin payload");
    expect(sub.tests).toBeUndefined();
  });
});

describe("console — SubmissionTestResults.vue template does not reference hidden fields", () => {
  it('source file is free of "isHidden" / "caseScope" / "is_hidden" / "case_scope" identifiers', () => {
    // Resolve relative to this spec file (import.meta.url) so the static scan is
    // path-agnostic — works on any clone path and in CI, not tied to a specific
    // worktree or machine. (task #10)
    const here = dirname(fileURLToPath(import.meta.url));
    const sourcePath = join(here, "..", "SubmissionTestResults.vue");
    const content = readFileSync(sourcePath, "utf8");
    expect(content).not.toMatch(/isHidden/);
    expect(content).not.toMatch(/is_hidden/);
    expect(content).not.toMatch(/caseScope/);
    expect(content).not.toMatch(/case_scope/);
  });
});
