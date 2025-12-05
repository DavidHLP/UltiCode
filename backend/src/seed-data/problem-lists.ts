const data = {
  problem_list_groups: [
    { id: 'group-created', name: 'Created by me', sort_order: 0 },
    { id: 'group-saved', name: 'Saved by me', sort_order: 1 },
  ],
  problem_lists: [
    {
      id: 'list-essentials',
      group_id: 'group-saved',
      name: 'Essential Problems',
      description: 'The absolute must-know patterns.',
      author_id: 'u-001',
      is_public: true,
      created_at: '2024-01-15T00:00:00.000Z',
      updated_at: '2024-02-02T00:00:00.000Z',
    },
  ],
  problem_list_relations: [
    { list_id: 'list-essentials', problem_id: 1 },
  ],
  problem_list_saved_relations: [
    { user_id: 'u-001', list_id: 'list-essentials' },
  ],
  problem_list_favorite_relations: [],
} as const;

export default data;
