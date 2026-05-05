export default {
  title: 'Audit Logs',
  searchPlaceholder: 'Search audit logs...',
  filterAction: 'Filter Action',
  allActions: 'All Actions',
  export: 'Export',
  noLogs: 'No Logs',
  noLogsDescription: 'No audit log records found matching your criteria.',
  systemAction: 'System Action',
  oldValues: 'Old Values',
  newValues: 'New Values',
  ipAddress: 'IP Address',
  userAgent: 'User Agent',

  // Column definitions
  columns: {
    createdAt: 'Time',
    action: 'Action',
    entityType: 'Entity Type',
    performer: 'Performer',
    target: 'Target',
    ip: 'IP Address',
    details: 'Details',
  },

  // Filters
  filters: {
    allActions: 'All Actions',
    allEntities: 'All Entities',
  },

  // Entity types
  entityTypes: {
    USER: 'User',
    PROBLEM: 'Problem',
    CONTEST: 'Contest',
    SOLUTION: 'Solution',
    FORUM_POST: 'Forum Post',
  },

  // Action types
  actionTypes: {
    CREATE_USER: 'Create User',
    UPDATE_USER: 'Update User',
    DELETE_USER: 'Delete User',
    BAN_USER: 'Ban User',
    UNBAN_USER: 'Unban User',
    GRANT_PERMISSION: 'Grant Permission',
    REVOKE_PERMISSION: 'Revoke Permission',
  },

  // Actions
  actions: {
    viewDetails: 'View Details',
    openMenu: 'Open menu',
    create: 'Create',
    update: 'Update',
    delete: 'Delete',
    publish: 'Publish',
    moderate: 'Moderate',
  },

  // Stats ticker labels
  stats: {
    total: 'total',
    create: 'create',
    update: 'update',
    delete: 'delete',
    systemAuditTrail: 'system audit trail',
  },

  // Detail drawer
  drawer: {
    description: 'Detailed record of the system event.',
    notFound: 'Select a log entry to view details',
    system: 'System',
    notAvailable: 'N/A',
    targetEntity: 'Target Entity',
    userAgent: 'User Agent',
    dataChanges: 'Data Changes',
    noDataChanges: 'No data changes recorded.',
    previousState: 'Previous State',
    newState: 'New State',
  },

  // Toast messages
  toast: {
    loadFailed: 'Failed to load audit logs',
  },
} as const
