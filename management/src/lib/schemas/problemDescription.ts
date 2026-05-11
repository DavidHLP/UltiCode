import { z } from 'zod'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

// Zod schemas using API enum values
export const DifficultyEnum = z.nativeEnum(Difficulty)
export const ProblemStatusEnum = z.nativeEnum(ProblemStatus)

export const exampleSchema = z.object({
  input: z.string(),
  output: z.string(),
  explanation: z.string().optional(),
})

export type Example = z.infer<typeof exampleSchema>

export const problemDescriptionSchema = z.object({
  title: z.string().min(1, 'Title is required').max(255, 'Title must be at most 255 characters'),
  slug: z
    .string()
    .min(1, 'Slug is required')
    .max(120, 'Slug must be at most 120 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug must contain only lowercase letters, numbers, and hyphens'),
  difficulty: DifficultyEnum,
  status: ProblemStatusEnum,
  isPremium: z.boolean(),
  isPublished: z.boolean(),
  summary: z.string().max(500, 'Summary must be at most 500 characters').optional(),
  content: z.string().min(1, 'Content is required'),
  examples: z.array(exampleSchema).min(1, 'At least one example is required'),
  constraints: z.array(z.string()).min(1, 'At least one constraint is required'),
  hints: z.array(z.string()).default([]),
  tags: z.array(z.string()).default([]),
  languages: z.array(z.string()).default([]),
})

export type ProblemDescriptionFormData = z.infer<typeof problemDescriptionSchema>