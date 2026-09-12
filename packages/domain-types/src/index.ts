/**
 * @ulticode/domain-types — cross-stack DTO contract shared by console + management.
 *
 * The cross-stack Problem contract is intentionally split into the two
 * audience shapes that have production callers: public console and admin
 * management. Wire compatibility is normalized here, at the API boundary.
 *
 * Keep audience-specific fields out of the other shape. Do not mirror every
 * backend DTO in this package.
 */

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export type ProblemDifficulty = 'EASY' | 'MEDIUM' | 'HARD'

export type PublicProblemStatus = 'solved' | 'attempted' | 'todo'

export type AdminProblemStatus =
  | 'SOLVED'
  | 'ATTEMPTED'
  | 'TODO'
  | 'solved'
  | 'attempted'
  | 'todo'

const PUBLIC_PROBLEM_STATUSES = new Set<PublicProblemStatus>([
  'solved',
  'attempted',
  'todo',
])

const ADMIN_PROBLEM_STATUSES = new Set<AdminProblemStatus>([
  'SOLVED',
  'ATTEMPTED',
  'TODO',
  'solved',
  'attempted',
  'todo',
])

export interface ProblemTag {
  id: string
  label: string
}

export interface ProblemPublic {
  id: number
  title: string
  slug: string
  difficulty: ProblemDifficulty
  acceptance_rate: number
  acceptanceRate?: number
  tags: string[]
  status?: PublicProblemStatus
  isPremium?: boolean
  hasSolution?: boolean
  completedTime?: string
  submissionCount?: number
  solutionCount?: number
  createdAt?: string
  updatedAt?: string
  sortOrder?: number
  addedAt?: string
}

export interface ProblemAdmin {
  id: string
  slug: string
  title: string
  difficulty: ProblemDifficulty
  acceptanceRate?: number
  status: AdminProblemStatus
  isPremium: boolean
  hasSolution: boolean
  isPublished: boolean
  publishedAt?: Date
  publishedBy?: string
  isDeleted: boolean
  deletedAt?: Date
  isFlagged?: boolean
  flagReason?: string
  flagReportedBy?: string
  flagReportedAt?: Date
  flagStatus?: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
  flagReviewedBy?: string
  flagReviewedAt?: Date
  flagNotes?: string
  createdAt: Date
  updatedAt: Date
  tags: ProblemTag[]
  submissionCount?: number
  solutionCount?: number
}

type ProblemRecord = Record<string, unknown>

const problemAliases: ReadonlyArray<readonly [string, string]> = [
  ['acceptanceRate', 'acceptance_rate'],
  ['isPremium', 'is_premium'],
  ['hasSolution', 'has_solution'],
  ['completedTime', 'completed_time'],
  ['isPublished', 'is_published'],
  ['publishedAt', 'published_at'],
  ['publishedBy', 'published_by'],
  ['isDeleted', 'is_deleted'],
  ['deletedAt', 'deleted_at'],
  ['isFlagged', 'is_flagged'],
  ['flagReason', 'flag_reason'],
  ['flagReportedBy', 'flag_reported_by'],
  ['flagReportedAt', 'flag_reported_at'],
  ['flagStatus', 'flag_status'],
  ['flagReviewedBy', 'flag_reviewed_by'],
  ['flagReviewedAt', 'flag_reviewed_at'],
  ['flagNotes', 'flag_notes'],
  ['submissionCount', 'submission_count'],
  ['solutionCount', 'solution_count'],
  ['createdAt', 'created_at'],
  ['updatedAt', 'updated_at'],
]

function asProblemRecord(value: unknown): ProblemRecord | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as ProblemRecord)
    : undefined
}

function canonicalProblemRecord(value: unknown): ProblemRecord | undefined {
  const raw = asProblemRecord(value)
  if (!raw) return undefined

  const result = { ...raw }
  for (const [camelKey, snakeKey] of problemAliases) {
    const resolved = result[camelKey] ?? raw[snakeKey]
    if (resolved !== undefined) result[camelKey] = resolved
    delete result[snakeKey]
  }
  return result
}

function asNumber(value: unknown): number | undefined {
  if (value === null || value === undefined) return undefined
  if (typeof value === 'string' && value.trim() === '') return undefined
  if (typeof value !== 'number' && typeof value !== 'string') return undefined
  const result = Number(value)
  return Number.isFinite(result) ? result : undefined
}

function asDate(value: unknown): Date | undefined {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? undefined : value
  }
  if (typeof value !== 'string') return undefined
  const result = new Date(value)
  return Number.isNaN(result.getTime()) ? undefined : result
}

function invalidProblem(audience: string, message: string): TypeError {
  return new TypeError(`${audience} Problem payload ${message}`)
}

function requiredRecord(value: unknown, audience: string): ProblemRecord {
  const record = canonicalProblemRecord(value)
  if (!record) throw invalidProblem(audience, 'must be an object')
  return record
}

function requiredText(record: ProblemRecord, key: string, audience: string): string {
  const value = record[key]
  if (typeof value !== 'string' || value.trim() === '') {
    throw invalidProblem(audience, `is missing a valid ${key}`)
  }
  return value
}

function requiredId(record: ProblemRecord, audience: string): number {
  const value = asNumber(record.id)
  if (value === undefined || !Number.isInteger(value) || value < 0) {
    throw invalidProblem(audience, 'is missing a valid id')
  }
  return value
}

function requiredDifficulty(record: ProblemRecord, audience: string): ProblemDifficulty {
  const difficulty = requiredText(record, 'difficulty', audience).toUpperCase()
  if (difficulty !== 'EASY' && difficulty !== 'MEDIUM' && difficulty !== 'HARD') {
    throw invalidProblem(audience, 'has an invalid difficulty')
  }
  return difficulty
}

function optionalRate(record: ProblemRecord, audience: string): number | undefined {
  const raw = record.acceptanceRate
  if (raw === null || raw === undefined) return undefined
  const rate = asNumber(raw)
  if (rate === undefined || rate < 0 || rate > 100) {
    throw invalidProblem(audience, 'has an invalid acceptanceRate')
  }
  return rate
}

function requiredRate(record: ProblemRecord, audience: string): number {
  const rate = optionalRate(record, audience)
  if (rate === undefined) throw invalidProblem(audience, 'is missing a valid acceptanceRate')
  return rate
}

function optionalStatus<T extends string>(
  record: ProblemRecord,
  key: string,
  allowed: ReadonlySet<T>,
  audience: string,
): T | undefined {
  const value = record[key]
  if (value === null || value === undefined) return undefined
  if (typeof value !== 'string' || !allowed.has(value as T)) {
    throw invalidProblem(audience, `has an invalid ${key}`)
  }
  return value as T
}

function requiredBoolean(record: ProblemRecord, key: string, audience: string): boolean {
  if (typeof record[key] !== 'boolean') {
    throw invalidProblem(audience, `is missing a valid ${key}`)
  }
  return record[key] as boolean
}

function optionalBoolean(
  record: ProblemRecord,
  key: string,
  audience: string,
): boolean | undefined {
  if (record[key] === null || record[key] === undefined) return undefined
  if (typeof record[key] !== 'boolean') {
    throw invalidProblem(audience, `has an invalid ${key}`)
  }
  return record[key] as boolean
}

function requiredDate(record: ProblemRecord, key: string, audience: string): Date {
  const date = asDate(record[key])
  if (!date) throw invalidProblem(audience, `is missing a valid ${key}`)
  return date
}

function problemTags(value: unknown, audience = 'Admin'): ProblemTag[] {
  if (!Array.isArray(value)) {
    throw invalidProblem(audience, 'must contain a tags array')
  }
  return value.map((tag, index) => {
    const record = asProblemRecord(tag)
    const id = record?.id
    const label = record?.label
    if (typeof id !== 'string' || id.trim() === '' || typeof label !== 'string' || label.trim() === '') {
      throw invalidProblem(audience, `has an invalid tags[${index}] entry`)
    }
    return { id, label }
  })
}

function publicTags(record: ProblemRecord): string[] {
  if (record.tags !== null && record.tags !== undefined && !Array.isArray(record.tags)) {
    throw invalidProblem('Public', 'must contain a tags array')
  }
  if (Array.isArray(record.tags)) {
    return record.tags.map((tag, index) => {
      const label = typeof tag === 'string' ? tag : asProblemRecord(tag)?.label
      if (typeof label !== 'string' || label.trim() === '') {
        throw invalidProblem('Public', `has an invalid tags[${index}] entry`)
      }
      return label
    })
  }
  if (
    record.tagRelations !== null &&
    record.tagRelations !== undefined &&
    !Array.isArray(record.tagRelations)
  ) {
    throw invalidProblem('Public', 'must contain a tagRelations array')
  }
  if (Array.isArray(record.tagRelations)) {
    return record.tagRelations.map((relation, index) => {
      const label = asProblemRecord(asProblemRecord(relation)?.tag)?.label
      if (typeof label !== 'string' || label.trim() === '') {
        throw invalidProblem('Public', `has an invalid tagRelations[${index}] entry`)
      }
      return label
    })
  }
  return []
}

export function normalizePublicProblem(value: unknown): ProblemPublic {
  const record = requiredRecord(value, 'Public')
  const id = requiredId(record, 'Public')
  const title = requiredText(record, 'title', 'Public')
  const slug = requiredText(record, 'slug', 'Public')
  const difficulty = requiredDifficulty(record, 'Public')
  const acceptanceRate = requiredRate(record, 'Public')
  const status = optionalStatus(record, 'status', PUBLIC_PROBLEM_STATUSES, 'Public')
  const completedTime =
    record.completedTime === null || record.completedTime === undefined
      ? undefined
      : record.completedTime instanceof Date
        ? record.completedTime.toISOString()
        : typeof record.completedTime === 'string'
          ? record.completedTime
          : (() => {
              throw invalidProblem('Public', 'has an invalid completedTime')
            })()
  return {
    ...record,
    id,
    title,
    slug,
    difficulty,
    acceptance_rate: acceptanceRate,
    acceptanceRate,
    status,
    isPremium: optionalBoolean(record, 'isPremium', 'Public'),
    hasSolution: optionalBoolean(record, 'hasSolution', 'Public'),
    completedTime,
    tags: publicTags(record),
  } as ProblemPublic
}

export function normalizeAdminProblem(value: unknown): ProblemAdmin {
  const record = requiredRecord(value, 'Admin')
  const id = requiredId(record, 'Admin')
  const slug = requiredText(record, 'slug', 'Admin')
  const title = requiredText(record, 'title', 'Admin')
  const difficulty = requiredDifficulty(record, 'Admin')
  const statusText = requiredText(record, 'status', 'Admin')
  if (!ADMIN_PROBLEM_STATUSES.has(statusText as AdminProblemStatus)) {
    throw invalidProblem('Admin', 'has an invalid status')
  }
  const status = statusText as AdminProblemStatus
  const isPremium = requiredBoolean(record, 'isPremium', 'Admin')
  const hasSolution = requiredBoolean(record, 'hasSolution', 'Admin')
  const isPublished = requiredBoolean(record, 'isPublished', 'Admin')
  const isDeleted = requiredBoolean(record, 'isDeleted', 'Admin')
  const acceptanceRate = optionalRate(record, 'Admin')
  const createdAt = requiredDate(record, 'createdAt', 'Admin')
  const updatedAt = requiredDate(record, 'updatedAt', 'Admin')
  const optionalDateKeys = [
    'publishedAt',
    'deletedAt',
    'flagReportedAt',
    'flagReviewedAt',
  ] as const
  for (const key of optionalDateKeys) {
    if (record[key] !== null && record[key] !== undefined) {
      const date = asDate(record[key])
      if (!date) throw invalidProblem('Admin', `has an invalid ${key}`)
      record[key] = date
    }
  }

  return {
    ...record,
    id: String(id),
    slug,
    title,
    difficulty,
    status,
    isPremium,
    hasSolution,
    isPublished,
    isDeleted,
    acceptanceRate,
    createdAt,
    updatedAt,
    tags: problemTags(record.tags),
  } as ProblemAdmin
}
