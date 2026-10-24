import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { toTypedSchema } from './toTypedSchema'

describe('Zod 4 form adapter', () => {
  const schema = toTypedSchema(
    z.object({
      name: z.string().min(1, 'Name required'),
      count: z.coerce.number().min(0),
      hints: z.array(z.string()).default([]),
      examples: z.array(z.object({ input: z.string().min(1, 'Input required') })),
      note: z.string().optional(),
    }),
  )

  it('preserves coercion, defaults and parsed submission values', async () => {
    expect(await schema.parse({ name: 'Test', count: '2', examples: [] })).toEqual({
      value: { name: 'Test', count: 2, hints: [], examples: [] },
      errors: [],
    })
    expect(schema.cast?.({ name: '' })).toMatchObject({ name: '', hints: [] })
  })

  it('reports field errors with vee-validate array paths', async () => {
    expect((await schema.parse({})).errors).toContainEqual({ path: 'name', errors: ['Required'] })
    const result = await schema.parse({ name: '', count: 0, examples: [{ input: '' }] })
    expect(result.errors).toContainEqual({ path: 'name', errors: ['Name required'] })
    expect(result.errors).toContainEqual({ path: 'examples[0].input', errors: ['Input required'] })
    expect(result.value).toBeUndefined()
    expect(schema.describe?.('examples[0].input')).toEqual({ exists: true, required: true })
    expect(schema.describe?.('note')).toEqual({ exists: true, required: false })
    expect(schema.describe?.('missing' as 'name')).toEqual({ exists: false, required: false })
  })

  it('rejects oversized constrained collections before validating their elements', async () => {
    let parsed = 0
    const bounded = toTypedSchema(
      z.object({
        items: z
          .array(
            z.string().refine(() => {
              parsed++
              return true
            }),
          )
          .max(2),
      }),
    )
    expect((await bounded.parse({ items: ['a', 'b', 'c'] })).errors).toHaveLength(1)
    expect(parsed).toBe(0)
  })
})
