import { z } from 'zod'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

// Zod schemas using API enum values
export const DifficultyEnum = z.nativeEnum(Difficulty)
export const ProblemStatusEnum = z.nativeEnum(ProblemStatus)

/** Maximum length of a problem summary; matches the backend column constraint. */
export const SUMMARY_MAX_LENGTH = 500

export const exampleSchema = z.object({
  id: z.string().optional(),
  input: z.string(),
  output: z.string(),
  explanation: z.string().optional(),
  inputs: z
    .array(
      z.object({
        name: z.string(),
        value: z.unknown(),
        label: z.string().optional(),
        fieldName: z.string().optional(),
      }),
    )
    .optional(),
})

export type Example = z.infer<typeof exampleSchema>

export const problemFormSchema = z.object({
  slug: z
    .string()
    .min(1, 'Slug is required')
    .max(120, 'Slug must be at most 120 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug must contain only lowercase letters, numbers, and hyphens'),
  title: z.string().min(1, 'Title is required').max(255, 'Title must be at most 255 characters'),
  difficulty: DifficultyEnum,
  status: ProblemStatusEnum.default(ProblemStatus.TODO),
  isPremium: z.boolean().default(false),
  isPublished: z.boolean().default(false),
  summary: z
    .string()
    .max(SUMMARY_MAX_LENGTH, `Summary must be at most ${SUMMARY_MAX_LENGTH} characters`)
    .optional(),
  content: z.string().optional(),
  examples: z.array(exampleSchema).min(1, 'At least one example is required').default([]),
  constraints: z.array(z.string()).default([]),
  hints: z.array(z.string()).default([]),
  languages: z.array(z.string()).default([]),
  tags: z.array(z.string()).default([]),
})

export type ProblemFormData = z.infer<typeof problemFormSchema>

// Schema for filtering problems in the list view
export const problemFilterSchema = z.object({
  search: z.string().optional(),
  difficulty: DifficultyEnum.optional(),
  status: ProblemStatusEnum.optional(),
  isPublished: z.boolean().optional(),
  isDeleted: z.boolean().optional(),
  tag: z.string().optional(),
  page: z.number().int().min(1).default(1),
  limit: z.number().int().min(1).max(100).default(20),
  sortBy: z.string().default('id'),
  sortOrder: z.enum(['asc', 'desc']).default('desc'),
})

export type ProblemFilter = z.infer<typeof problemFilterSchema>
