const data = {
  problems: [
    {
      id: 1,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'Easy',
      acceptance_rate: 49.2,
      status: 'todo',
      is_premium: false,
      has_solution: true,
      completed_time: null,
    },
  ],
  problem_tags: [
    { id: 'array', label: 'Array' },
    { id: 'hash-table', label: 'Hash Table' },
  ],
  problem_tag_relations: [
    { problem_id: 1, tag_id: 'array' },
    { problem_id: 1, tag_id: 'hash-table' },
  ],
} as const;

export default data;
