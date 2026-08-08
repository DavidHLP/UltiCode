export default {
  title: 'Audit Report',
  description: 'System activity analysis and statistics',

  // Filters section
  filters: 'Filters',
  startDate: 'Start Date',
  endDate: 'End Date',
  performer: 'Performer ID',
  performerPlaceholder: 'Enter performer ID...',
  applyFilters: 'Apply Filters',
  export: 'Export CSV',

  // Stats overview
  totalActions: 'Total Actions',
  allTime: 'All Time',
  uniqueEntities: 'Unique Entities',
  entityTypes: 'Entity Types',
  activePerformers: 'Active Performers',
  users: 'Users',

  // Top performers section
  topPerformers: 'Top Performers',
  actions: 'actions',

  // Actions by entity section
  actionsByEntity: 'Actions by Entity',

  // Actions by type section
  actionsByType: 'Actions by Type',

  // Additional filters
  userId: 'Target User ID',
  userIdPlaceholder: 'Enter target user ID...',
  selectEntityTypeFirst: 'Select entity type first',
  searchPlaceholder: 'Search actions, entities...',
  apply: 'Apply',
  reset: 'Reset',

  // Empty state
  noData: 'No data available',
} as const
