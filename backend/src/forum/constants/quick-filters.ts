/**
 * Static quick filter configuration for forum
 * Previously imported from init-db/seeds/data/forum.data
 */

export interface QuickFilter {
  id: string;
  label: string;
  value: string;
}

export const FORUM_QUICK_FILTERS: QuickFilter[] = [
  { id: 'filter-new', label: 'New', value: 'new' },
  { id: 'filter-top', label: 'Top', value: 'top' },
  { id: 'filter-hot', label: 'Hot', value: 'hot' },
] as const;
