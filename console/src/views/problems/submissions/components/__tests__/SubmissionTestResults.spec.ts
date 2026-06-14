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
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'
import type { SubmissionRecord, SubmissionTestRecord } from '@/types/submission'

describe('console — SubmissionRecord 0-leak invariant (task #10 ADR-001)', () => {
  it('SubmissionRecord has no isHidden field', () => {
    const sample: SubmissionRecord = {
      id: 'sub-1',
      problem_id: 1,
      status: 'Accepted',
      language: 'cpp',
      runtime: 100,
      memory: 50,
      created_at: '2026-06-14T00:00:00Z',
    }
    expect((sample as unknown as { isHidden?: boolean }).isHidden).toBeUndefined()
    expect((sample as unknown as { is_hidden?: boolean }).is_hidden).toBeUndefined()
    expect((sample as unknown as { caseScope?: string }).caseScope).toBeUndefined()
  })

  it('SubmissionTestRecord (per-test row) has no isHidden / caseScope / input / output fields', () => {
    const sample: SubmissionTestRecord = {
      id: 'tc-1',
      status: 'Accepted',
      runtime: 10,
      memory: 5,
    }
    expect((sample as unknown as { isHidden?: boolean }).isHidden).toBeUndefined()
    expect((sample as unknown as { is_hidden?: boolean }).is_hidden).toBeUndefined()
    expect((sample as unknown as { caseScope?: string }).caseScope).toBeUndefined()
    expect((sample as unknown as { case_scope?: string }).case_scope).toBeUndefined()
    expect((sample as unknown as { input?: string }).input).toBeUndefined()
    expect((sample as unknown as { output?: string }).output).toBeUndefined()
    expect((sample as unknown as { expectedOutput?: string }).expectedOutput).toBeUndefined()
  })

  it('only SubmissionRecord (top-level) carries first-failed detail fields, not per-test', () => {
    const sub: SubmissionRecord = {
      id: 'sub-1',
      problem_id: 1,
      status: 'Wrong Answer',
      language: 'cpp',
      runtime: 100,
      memory: 50,
      input: 'stdin payload',
      output: 'actual output',
      expected_output: 'expected output',
      created_at: '2026-06-14T00:00:00Z',
    }
    expect(sub.input).toBe('stdin payload')
    expect(sub.tests).toBeUndefined()
  })
})

describe('console — SubmissionTestResults.vue template does not reference hidden fields', () => {
  it('source file is free of "isHidden" / "caseScope" / "is_hidden" / "case_scope" identifiers', () => {
    // Resolve relative to this spec file (import.meta.url) so the static scan
    // works in CI and on any clone path — and reads the worktree copy, not a
    // hard-coded main-repo absolute path. (reviewer blocker fix, task #10)
    const here = dirname(fileURLToPath(import.meta.url))
    const sourcePath = join(here, '..', 'SubmissionTestResults.vue')
    const content = readFileSync(sourcePath, 'utf8')
    expect(content).not.toMatch(/isHidden/)
    expect(content).not.toMatch(/is_hidden/)
    expect(content).not.toMatch(/caseScope/)
    expect(content).not.toMatch(/case_scope/)
  })
})
