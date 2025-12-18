import { USER_IDS } from './users.data';

const data = {
  problem_list_groups: [
    { id: 'group-created', name: 'Created by me', sort_order: 0 },
    { id: 'group-saved', name: 'Saved by me', sort_order: 1 },
    { id: 'group-featured', name: 'Featured Playlists', sort_order: 2 },
    { id: 'group-practice', name: 'Weekly Practice', sort_order: 3 },
  ],
  problem_lists: [
    {
      id: 'list-essentials',
      group_id: 'group-saved',
      name: 'Essential Problems',
      description: 'The absolute must-know patterns.',
      author_id: USER_IDS.SHADCN,
      is_public: true,
      created_at: '2024-01-15T00:00:00.000Z',
      updated_at: '2024-02-02T00:00:00.000Z',
    },
    {
      id: 'list-sliding-window',
      group_id: 'group-featured',
      name: 'Sliding Window Classics',
      description:
        'Strings and arrays that force you to manage window boundaries correctly.',
      author_id: USER_IDS.SARA,
      is_public: true,
      created_at: '2024-03-10T00:00:00.000Z',
      updated_at: '2024-04-01T00:00:00.000Z',
    },
    {
      id: 'list-intervals',
      group_id: 'group-featured',
      name: 'Intervals & Sorting',
      description:
        'Sweep line, merging, and ordering exercises seen in contests.',
      author_id: USER_IDS.CHEN,
      is_public: true,
      created_at: '2024-05-08T00:00:00.000Z',
      updated_at: '2024-05-20T00:00:00.000Z',
    },
    {
      id: 'list-graph-dfs',
      group_id: 'group-practice',
      name: 'Graph DFS/BFS Warm-up',
      description:
        'Quick traversal problems to drill grid and graph intuition.',
      author_id: USER_IDS.DAVID,
      is_public: true,
      created_at: '2024-06-12T00:00:00.000Z',
      updated_at: '2024-06-20T00:00:00.000Z',
    },
    {
      id: 'list-hard-bench',
      group_id: 'group-practice',
      name: 'Hard Benchmarks',
      description:
        'Curated hard problems for interview prep and contest training.',
      author_id: USER_IDS.PETR,
      is_public: false,
      created_at: '2024-07-01T00:00:00.000Z',
      updated_at: '2024-07-15T00:00:00.000Z',
    },
  ],
  problem_list_relations: [
    { list_id: 'list-essentials', problem_id: 1 },
    { list_id: 'list-sliding-window', problem_id: 1 },
    { list_id: 'list-sliding-window', problem_id: 2 },
    { list_id: 'list-intervals', problem_id: 3 },
    { list_id: 'list-intervals', problem_id: 1 },
    { list_id: 'list-graph-dfs', problem_id: 5 },
    { list_id: 'list-hard-bench', problem_id: 4 },
    { list_id: 'list-hard-bench', problem_id: 3 },
  ],
  problem_list_saved_relations: [
    { user_id: USER_IDS.SHADCN, list_id: 'list-essentials' },
    { user_id: USER_IDS.SHADCN, list_id: 'list-sliding-window' },
    { user_id: USER_IDS.YUKI, list_id: 'list-intervals' },
    { user_id: USER_IDS.YUKI, list_id: 'list-hard-bench' },
  ],
  problem_list_favorite_relations: [
    { user_id: USER_IDS.ALEX, list_id: 'list-sliding-window' },
    { user_id: USER_IDS.CHEN, list_id: 'list-intervals' },
  ],
} as const;

export default data;
