import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  useContestAuthoring,
  DEFAULT_PROBLEM_SCORE,
  DEFAULT_DURATION_MINUTES,
} from './useContestAuthoring'
import { contestsApi } from '@/api/admin/contests'
import { ContestType } from '@/api/admin/contests'
import type { Contest, ContestProblem } from '@/api/admin/contests'

vi.mock('@/api/admin/contests', async () => {
  const actual =
    await vi.importActual<typeof import('@/api/admin/contests')>('@/api/admin/contests')
  return {
    ...actual,
    contestsApi: {
      createContest: vi.fn(),
      addProblem: vi.fn(),
    },
  }
})

const mockedCreateContest = vi.mocked(contestsApi.createContest)
const mockedAddProblem = vi.mocked(contestsApi.addProblem)

/**
 * Fill the draft with a valid shape so validators/payload builders can run
 * without unrelated guards firing. Each test overrides the fields it cares
 * about via `overrides`.
 */
function seedValidDraft(
  patchBasicInfo: ReturnType<typeof useContestAuthoring>['patchBasicInfo'],
  patchSchedule: ReturnType<typeof useContestAuthoring>['patchSchedule'],
) {
  patchBasicInfo({
    title: 'Weekly Contest #1',
    slug: 'weekly-contest-1',
    description: 'A description',
    contestType: ContestType.ICPC,
  })
  patchSchedule({
    startTimeLocal: '2099-01-01T10:00',
    duration: 120,
    isPublished: false,
  })
}

describe('useContestAuthoring', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('initial draft + reset', () => {
    it('starts with the documented initial shape', () => {
      const { reviewSlice, reset } = useContestAuthoring()
      expect(reviewSlice.value).toEqual({
        title: '',
        slug: '',
        description: '',
        contestType: ContestType.ICPC,
        scoringRuleId: '',
        startTimeLocal: '',
        duration: DEFAULT_DURATION_MINUTES,
        isPublished: false,
        problems: [],
      })
      // sanity: reset is a no-op on a fresh instance
      reset()
      expect(reviewSlice.value.problems).toEqual([])
    })

    it('reset returns the draft to the initial shape after edits', () => {
      const { patchBasicInfo, patchSchedule, addProblem, setProblemScore, reviewSlice, reset } =
        useContestAuthoring()

      patchBasicInfo({ title: 'X', slug: 'x', description: 'd' })
      patchSchedule({ duration: 60, isPublished: true })
      addProblem({ id: '7', title: 'P', slug: 'p', difficulty: 'EASY' })
      setProblemScore('7', 250)
      expect(reviewSlice.value.title).toBe('X')
      expect(reviewSlice.value.problems).toHaveLength(1)

      reset()
      expect(reviewSlice.value).toEqual({
        title: '',
        slug: '',
        description: '',
        contestType: ContestType.ICPC,
        scoringRuleId: '',
        startTimeLocal: '',
        duration: DEFAULT_DURATION_MINUTES,
        isPublished: false,
        problems: [],
      })
    })
  })

  describe('per-step invariant validators', () => {
    it('basicInfoValid rejects an empty title', () => {
      const { patchBasicInfo, basicInfoValid } = useContestAuthoring()
      expect(basicInfoValid.value).toBe(false)
      patchBasicInfo({ title: '   ' })
      expect(basicInfoValid.value).toBe(false)
      patchBasicInfo({ title: 'ok' })
      expect(basicInfoValid.value).toBe(true)
    })

    it('scheduleValid rejects missing, invalid datetime-local, and non-positive duration', () => {
      const { patchSchedule, scheduleValid } = useContestAuthoring()
      expect(scheduleValid.value).toBe(false) // empty start time + default duration

      patchSchedule({ startTimeLocal: 'not-a-real-date' })
      expect(scheduleValid.value).toBe(false)

      patchSchedule({ startTimeLocal: '2099-01-01T10:00', duration: 0 })
      expect(scheduleValid.value).toBe(false)

      patchSchedule({ duration: -5 })
      expect(scheduleValid.value).toBe(false)

      patchSchedule({ duration: 90 })
      expect(scheduleValid.value).toBe(true)
    })

    it('scoringRuleValid and problemsValid default to true (optional step)', () => {
      const { scoringRuleValid, problemsValid, canSubmit } = useContestAuthoring()
      expect(scoringRuleValid.value).toBe(true)
      expect(problemsValid.value).toBe(true)
      // canSubmit still false because basic info + schedule are not valid yet
      expect(canSubmit.value).toBe(false)
    })
  })

  describe('problem actions', () => {
    it('addProblem injects the default score and ignores duplicates', () => {
      const { addProblem, problemsSlice, setProblemScore } = useContestAuthoring()
      addProblem({ id: '1', title: 'A', slug: 'a', difficulty: 'EASY' })
      addProblem({ id: '1', title: 'A', slug: 'a', difficulty: 'EASY' }) // dup
      addProblem({ id: '2', title: 'B', slug: 'b', difficulty: 'HARD' })

      expect(problemsSlice.value.problems).toEqual([
        { id: '1', slug: 'a', title: 'A', difficulty: 'EASY', score: DEFAULT_PROBLEM_SCORE },
        { id: '2', slug: 'b', title: 'B', difficulty: 'HARD', score: DEFAULT_PROBLEM_SCORE },
      ])

      setProblemScore('2', 250)
      expect(problemsSlice.value.problems[1].score).toBe(250)

      setProblemScore('unknown', 999) // no-op for missing id
      expect(problemsSlice.value.problems[1].score).toBe(250)
    })

    it('removeProblem filters by id', () => {
      const { addProblem, removeProblem, problemsSlice } = useContestAuthoring()
      addProblem({ id: '1', title: 'A', slug: 'a', difficulty: 'EASY' })
      addProblem({ id: '2', title: 'B', slug: 'b', difficulty: 'HARD' })
      removeProblem('1')
      expect(problemsSlice.value.problems.map((p) => p.id)).toEqual(['2'])
    })
  })

  describe('buildCreatePayload', () => {
    it('includes slug and converts startTimeLocal to ISO 8601', () => {
      const { patchBasicInfo, patchSchedule, setScoringRuleId, buildCreatePayload } =
        useContestAuthoring()
      seedValidDraft(
        (p) => patchBasicInfo(p),
        (p) => patchSchedule(p),
      )
      setScoringRuleId('rule-42')

      const payload = buildCreatePayload()
      expect(payload.title).toBe('Weekly Contest #1')
      expect(payload.slug).toBe('weekly-contest-1')
      expect(payload.contestType).toBe(ContestType.ICPC)
      expect(payload.scoringRuleId).toBe('rule-42')
      expect(payload.duration).toBe(120)
      expect(typeof payload.startTime).toBe('string')
      expect(payload.startTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/)
    })

    it('forwards empty description/scoringRuleId as undefined and never sends problemIds', () => {
      const { patchBasicInfo, patchSchedule, buildCreatePayload } = useContestAuthoring()
      patchBasicInfo({ title: 'T', slug: 't' })
      patchSchedule({ startTimeLocal: '2099-01-01T10:00', duration: 60 })

      const payload = buildCreatePayload()
      expect(payload.description).toBeUndefined()
      expect(payload.scoringRuleId).toBeUndefined()
      expect(payload).not.toHaveProperty('problemIds')
    })

    it('throws on invalid start time', () => {
      const { buildCreatePayload } = useContestAuthoring()
      expect(() => buildCreatePayload()).toThrow(/Invalid start time/)
    })
  })

  describe('buildProblemScorePatches', () => {
    it('produces one {problemId, score} per drafted problem with numeric ids', () => {
      const { addProblem, setProblemScore, buildProblemScorePatches } = useContestAuthoring()
      addProblem({ id: '10', title: 'A', slug: 'a', difficulty: 'EASY' })
      addProblem({ id: '20', title: 'B', slug: 'b', difficulty: 'HARD' })
      setProblemScore('20', 250)

      expect(buildProblemScorePatches()).toEqual([
        { problemId: 10, score: 100 },
        { problemId: 20, score: 250 },
      ])
    })
  })

  describe('submit orchestration', () => {
    it('creates the contest with slug and then issues one scored addProblem per problem', async () => {
      const { patchBasicInfo, patchSchedule, addProblem, setProblemScore, submit } =
        useContestAuthoring()
      seedValidDraft(
        (p) => patchBasicInfo(p),
        (p) => patchSchedule(p),
      )
      addProblem({ id: '10', title: 'A', slug: 'a', difficulty: 'EASY' })
      addProblem({ id: '20', title: 'B', slug: 'b', difficulty: 'HARD' })
      setProblemScore('20', 250)

      const created: Contest = {
        id: 'contest-xyz',
        slug: 'weekly-contest-1',
        title: 'Weekly Contest #1',
        contestType: ContestType.ICPC,
        startTime: '2026-07-19T00:00:00Z',
        duration: 120,
        status: 'DRAFT' as Contest['status'],
        isVisible: true,
        isPremium: false,
        isPublished: false,
        participantCount: 0,
        problemCount: 0,
      }
      mockedCreateContest.mockResolvedValue(created)
      const addedProblem: ContestProblem = {
        id: 'cp-1',
        contestId: 'contest-xyz',
        problemId: 10,
        problemIndex: 'A',
        score: 100,
      }
      mockedAddProblem.mockResolvedValue(addedProblem)

      const result = await submit()

      expect(result).toBe(created)
      expect(mockedCreateContest).toHaveBeenCalledTimes(1)
      const payload = mockedCreateContest.mock.calls[0]![0] as {
        slug?: string
        problemIds?: number[]
      }
      expect(payload.slug).toBe('weekly-contest-1')
      expect(payload.problemIds).toBeUndefined()

      expect(mockedAddProblem).toHaveBeenCalledTimes(2)
      expect(mockedAddProblem.mock.calls[0]).toEqual(['contest-xyz', { problemId: 10, score: 100 }])
      expect(mockedAddProblem.mock.calls[1]).toEqual(['contest-xyz', { problemId: 20, score: 250 }])
    })

    it('succeeds with zero addProblem calls when no problems are drafted', async () => {
      const { patchBasicInfo, patchSchedule, submit } = useContestAuthoring()
      seedValidDraft(
        (p) => patchBasicInfo(p),
        (p) => patchSchedule(p),
      )
      const created: Contest = {
        id: 'c',
        slug: 's',
        title: 't',
        contestType: ContestType.ICPC,
        startTime: '2026-07-19T00:00:00Z',
        duration: 120,
        status: 'DRAFT' as Contest['status'],
        isVisible: true,
        isPremium: false,
        isPublished: false,
        participantCount: 0,
        problemCount: 0,
      }
      mockedCreateContest.mockResolvedValue(created)

      await submit()

      expect(mockedCreateContest).toHaveBeenCalledTimes(1)
      expect(mockedAddProblem).not.toHaveBeenCalled()
    })

    it('surfaces the first addProblem failure and leaves earlier problems persisted (N-call window)', async () => {
      const { patchBasicInfo, patchSchedule, addProblem, submitting, submitError, submit } =
        useContestAuthoring()
      seedValidDraft(
        (p) => patchBasicInfo(p),
        (p) => patchSchedule(p),
      )
      addProblem({ id: '1', title: 'A', slug: 'a', difficulty: 'EASY' })
      addProblem({ id: '2', title: 'B', slug: 'b', difficulty: 'HARD' })

      const stubContest: Contest = {
        id: 'c',
        slug: 's',
        title: 't',
        contestType: ContestType.ICPC,
        startTime: '2026-07-19T00:00:00Z',
        duration: 120,
        status: 'DRAFT' as Contest['status'],
        isVisible: true,
        isPremium: false,
        isPublished: false,
        participantCount: 0,
        problemCount: 0,
      }
      const stubProblem: ContestProblem = {
        id: 'cp-x',
        contestId: 'c',
        problemId: 1,
        problemIndex: 'A',
        score: 0,
      }
      mockedCreateContest.mockResolvedValue(stubContest)
      mockedAddProblem
        .mockResolvedValueOnce(stubProblem)
        .mockRejectedValueOnce(new Error('boom'))

      await expect(submit()).rejects.toThrow('boom')

      // First problem still got its scored POST through before the failure.
      expect(mockedAddProblem).toHaveBeenCalledTimes(2)
      expect(submitting.value).toBe(false)
      expect(submitError.value).toBe('boom')
    })

    it('clears submitting in finally on the success path', async () => {
      const { patchBasicInfo, patchSchedule, submitting, submit } = useContestAuthoring()
      seedValidDraft(
        (p) => patchBasicInfo(p),
        (p) => patchSchedule(p),
      )
      expect(submitting.value).toBe(false) // initial state
      const stubContest: Contest = {
        id: 'c',
        slug: 's',
        title: 't',
        contestType: ContestType.ICPC,
        startTime: '2026-07-19T00:00:00Z',
        duration: 120,
        status: 'DRAFT' as Contest['status'],
        isVisible: true,
        isPremium: false,
        isPublished: false,
        participantCount: 0,
        problemCount: 0,
      }
      mockedCreateContest.mockResolvedValue(stubContest)

      await submit()

      // `finally` ran: submitting is back to false even though submit succeeded.
      expect(submitting.value).toBe(false)
    })

    it('clears submitting in finally on the failure path', async () => {
      const { patchBasicInfo, patchSchedule, submitting, submitError, submit } =
        useContestAuthoring()
      seedValidDraft(
        (p) => patchBasicInfo(p),
        (p) => patchSchedule(p),
      )
      mockedCreateContest.mockRejectedValue(new Error('create-failed'))

      await expect(submit()).rejects.toThrow('create-failed')

      // `finally` ran despite the rejection.
      expect(submitting.value).toBe(false)
      expect(submitError.value).toBe('create-failed')
    })
  })
})
