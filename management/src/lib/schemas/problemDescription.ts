import { z } from 'zod'
import {
  DifficultyEnum,
  ProblemStatusEnum,
  SUMMARY_MAX_LENGTH,
  exampleSchema,
  type Example,
} from './problem'

// Shared authoring building blocks (exampleSchema, DifficultyEnum,
// ProblemStatusEnum) live in ./problem so create and edit flows validate the
// same Example shape and enum surface. Previously this file redefined all
// three and the Example diverged (create examples carried an optional `id`,
// edit examples did not), so the two flows disagreed on the same domain.

export { exampleSchema, type Example }

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
  summary: z
    .string()
    .max(SUMMARY_MAX_LENGTH, `Summary must be at most ${SUMMARY_MAX_LENGTH} characters`)
    .optional(),
  content: z.string().min(1, 'Content is required'),
  examples: z.array(exampleSchema).min(1, 'At least one example is required'),
  constraints: z.array(z.string()).min(1, 'At least one constraint is required'),
  hints: z.array(z.string()).default([]),
  tags: z.array(z.string()).default([]),
  languages: z.array(z.string()).default([]),
})

export type ProblemDescriptionFormData = z.infer<typeof problemDescriptionSchema>
