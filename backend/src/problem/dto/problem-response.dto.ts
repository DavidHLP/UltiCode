import { BigIntUtil } from '../../common/utils/bigint.util';

/**
 * Consistent problem response that always includes hasAccess flag
 * Premium fields are conditionally included based on access
 */
export class ProblemResponseDto {
  id: string;
  slug: string;
  title: string;
  difficulty: string;
  is_premium: boolean;
  acceptance_rate: number;
  hasAccess: boolean;
  // Premium-only fields (only included if hasAccess is true)
  detail?: unknown;
  examples?: unknown[];
  languages?: string[];
  status?: string;
  completed_time?: Date | null;
}

/**
 * Teaser response for premium problems without access
 * Always returns consistent structure with hasAccess: false
 */
export class ProblemTeaserDto {
  id: string;
  slug: string;
  title: string;
  difficulty: string;
  is_premium: boolean;
  acceptance_rate: number;
  hasAccess: boolean;
}

/**
 * Convert a problem entity to response DTO with premium check
 */
export function toProblemResponseDto(
  problem: Record<string, unknown>,
  hasAccess: boolean,
): ProblemResponseDto | ProblemTeaserDto {
  const baseResponse = {
    id: BigIntUtil.toString(problem.id as bigint | string),
    slug: problem.slug as string,
    title: problem.title as string,
    difficulty: problem.difficulty as string,
    is_premium: problem.is_premium as boolean,
    acceptance_rate: Number(problem.acceptance_rate),
    hasAccess,
  };

  if (!hasAccess && (problem.is_premium as boolean)) {
    return baseResponse as ProblemTeaserDto;
  }

  return {
    ...baseResponse,
    detail: hasAccess ? problem.detail : undefined,
    examples: hasAccess ? problem.examples : undefined,
    languages: hasAccess ? problem.languages : undefined,
  } as ProblemResponseDto;
}
