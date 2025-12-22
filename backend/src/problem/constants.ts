export const PROBLEM_CATEGORIES = {
  ALGORITHMS: 'algorithms',
  DATABASE: 'database',
  SHELL: 'shell',
  CONCURRENCY: 'concurrency',
} as const;

export const CATEGORY_TAG_MAP: Record<string, string> = {
  [PROBLEM_CATEGORIES.ALGORITHMS]: 'Algorithms',
  [PROBLEM_CATEGORIES.DATABASE]: 'Database',
  [PROBLEM_CATEGORIES.SHELL]: 'Shell',
  [PROBLEM_CATEGORIES.CONCURRENCY]: 'Concurrency',
};
