import { Transform } from 'class-transformer';

/**
 * Custom decorator to transform string 'true'/'false' values to actual boolean types.
 * Handles query parameter parsing where boolean values come as strings from URL params.
 *
 * @example
 * ```typescript
 * @IsQueryBoolean()
 * @IsBoolean()
 * @IsOptional()
 * is_flagged?: boolean;
 * ```
 */
export function IsQueryBoolean() {
  return Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  });
}
