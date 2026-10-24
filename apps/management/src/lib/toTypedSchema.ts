import { z } from 'zod'
import type { TypedSchema } from 'vee-validate'

// vee-validate's published Zod adapter requires Zod 3; keep the form contract on patched Zod 4.
export function toTypedSchema<T extends z.ZodObject>(
  schema: T,
): TypedSchema<Partial<z.input<T>>, z.output<T>> {
  return {
    __type: 'VVTypedSchema',
    async parse(values) {
      const result = await schema.safeParseAsync(values, {
        error: (issue) =>
          issue.code === 'invalid_type' && issue.input === undefined ? 'Required' : undefined,
      })
      if (result.success) return { value: result.data, errors: [] }
      const errors = new Map<string, string[]>()
      for (const issue of result.error.issues) {
        const path = issue.path.reduce<string>(
          (path, part) =>
            typeof part === 'number'
              ? `${path}[${part}]`
              : path
                ? `${path}.${String(part)}`
                : String(part),
          '',
        )
        errors.set(path, [...(errors.get(path) ?? []), issue.message])
      }
      return { errors: Array.from(errors, ([path, errors]) => ({ path, errors })) }
    },
    cast(values) {
      const result = schema.safeParse(values)
      if (result.success) return result.data as Partial<z.input<T>>
      const defaults = Object.fromEntries(
        Object.entries(schema.shape).map(([key, field]) => {
          const result = field.safeParse(undefined)
          return [key, result.success ? result.data : undefined]
        }),
      )
      return { ...defaults, ...values }
    },
    describe(path) {
      let field: z.core.$ZodType | undefined = schema
      for (const part of String(path ?? '')
        .split(/[.[\]]/)
        .filter(Boolean)) {
        if (field instanceof z.ZodObject) field = field.shape[part]
        else if (field instanceof z.ZodArray && /^\d+$/.test(part)) field = field.element
        else field = undefined
      }
      return { required: field ? !z.safeParse(field, undefined).success : false, exists: !!field }
    },
  }
}
