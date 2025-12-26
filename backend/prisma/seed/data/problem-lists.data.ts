import { USER_IDS } from './users.data';
import { PROBLEM_IDS } from './problems.data';

const data = {
  problem_lists: [
    {
      id: 'list-essentials',
      name: 'Essential Problems',
      description: 'The absolute must-know patterns.',
      author_id: USER_IDS.SHADCN,
      is_public: true,
      is_featured: true,
      banner_tag: 'Essential',
      banner_icon: 'Trophy',
      banner_theme: 'amber',
      banner_order: 1,
      created_at: '2024-01-15T00:00:00.000Z',
      updated_at: '2024-02-02T00:00:00.000Z',
    },
    {
      id: 'list-sliding-window',
      name: 'Sliding Window Classics',
      description:
        'Strings and arrays that force you to manage window boundaries correctly.',
      author_id: USER_IDS.SARA,
      is_public: true,
      is_featured: true,
      banner_tag: 'Pattern',
      banner_icon: 'Code2',
      banner_theme: 'sky',
      banner_order: 2,
      created_at: '2024-03-10T00:00:00.000Z',
      updated_at: '2024-04-01T00:00:00.000Z',
    },
    {
      id: 'list-intervals',
      name: 'Intervals & Sorting',
      description:
        'Sweep line, merging, and ordering exercises seen in contests.',
      author_id: USER_IDS.CHEN,
      is_public: true,
      is_featured: true,
      banner_tag: 'Sorting',
      banner_icon: 'ArrowUpDown',
      banner_theme: 'emerald',
      banner_order: 3,
      created_at: '2024-05-08T00:00:00.000Z',
      updated_at: '2024-05-20T00:00:00.000Z',
    },
    {
      id: 'list-graph-dfs',
      name: 'Graph DFS/BFS Warm-up',
      description:
        'Quick traversal problems to drill grid and graph intuition.',
      author_id: USER_IDS.DAVID,
      is_public: true,
      is_featured: false,
      banner_tag: null,
      banner_icon: null,
      banner_theme: null,
      banner_order: 0,
      created_at: '2024-06-12T00:00:00.000Z',
      updated_at: '2024-06-20T00:00:00.000Z',
    },
    {
      id: 'list-hard-bench',
      name: 'Hard Benchmarks',
      description:
        'Curated hard problems for interview prep and contest training.',
      author_id: USER_IDS.PETR,
      is_public: false,
      is_featured: false,
      banner_tag: null,
      banner_icon: null,
      banner_theme: null,
      banner_order: 0,
      created_at: '2024-07-01T00:00:00.000Z',
      updated_at: '2024-07-15T00:00:00.000Z',
    },
  ],
  problem_list_relations: [
    { list_id: 'list-essentials', problem_id: PROBLEM_IDS.TWO_SUM },
    { list_id: 'list-essentials', problem_id: PROBLEM_IDS.COMBINE_TWO_TABLES },
    { list_id: 'list-essentials', problem_id: PROBLEM_IDS.TENTH_LINE },
    {
      list_id: 'list-essentials',
      problem_id: PROBLEM_IDS.PRINT_FOOBAR_ALTERNATELY,
    },
    {
      list_id: 'list-essentials',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
    },
    { list_id: 'list-sliding-window', problem_id: PROBLEM_IDS.TWO_SUM },
    {
      list_id: 'list-sliding-window',
      problem_id: PROBLEM_IDS.LONGEST_SUBSTRING,
    },
    { list_id: 'list-intervals', problem_id: PROBLEM_IDS.MERGE_INTERVALS },
    { list_id: 'list-intervals', problem_id: PROBLEM_IDS.TWO_SUM },
    { list_id: 'list-graph-dfs', problem_id: PROBLEM_IDS.NUMBER_OF_ISLANDS },
    {
      list_id: 'list-hard-bench',
      problem_id: PROBLEM_IDS.MEDIAN_OF_TWO_SORTED_ARRAYS,
    },
    { list_id: 'list-hard-bench', problem_id: PROBLEM_IDS.MERGE_INTERVALS },
  ],
  // User favorites: which users have favorited which lists
  user_problem_list_favorites: [
    { user_id: USER_IDS.SHADCN, list_id: 'list-sliding-window' },
    { user_id: USER_IDS.YUKI, list_id: 'list-essentials' },
    { user_id: USER_IDS.YUKI, list_id: 'list-intervals' },
    { user_id: USER_IDS.ALEX, list_id: 'list-sliding-window' },
    { user_id: USER_IDS.CHEN, list_id: 'list-essentials' },
  ],
  // User categories: custom categories for organizing saved lists
  user_problem_list_categories: [
    {
      id: 'cat-yuki-weekly',
      user_id: USER_IDS.YUKI,
      name: 'Weekly Practice',
      sort_order: 0,
    },
    {
      id: 'cat-yuki-interview',
      user_id: USER_IDS.YUKI,
      name: 'Interview Prep',
      sort_order: 1,
    },
  ],
  // User category items: map favorited lists into categories
  user_problem_list_category_items: [
    {
      user_id: USER_IDS.YUKI,
      list_id: 'list-essentials',
      category_id: 'cat-yuki-weekly',
    },
    {
      user_id: USER_IDS.YUKI,
      list_id: 'list-intervals',
      category_id: 'cat-yuki-interview',
    },
  ],
} as const;

export default data;
