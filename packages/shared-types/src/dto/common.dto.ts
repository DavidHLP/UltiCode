/**
 * Common search parameters
 */
export interface SearchParams {
  search?: string
}

/**
 * Date range parameters
 */
export interface DateRangeParams {
  startDate?: string
  endDate?: string
}

/**
 * ID parameter for single resource queries
 */
export interface IdParam {
  id: string
}

/**
 * Slug parameter for single resource queries
 */
export interface SlugParam {
  slug: string
}
