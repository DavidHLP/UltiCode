export default {
  // Actions
  save: 'Save',
  cancel: 'Cancel',
  delete: 'Delete',
  edit: 'Edit',
  add: 'Add',
  create: 'Create',
  update: 'Update',
  view: 'View',
  remove: 'Remove',
  confirm: 'Confirm',
  submit: 'Submit',
  back: 'Back',
  next: 'Next',
  previous: 'Previous',
  close: 'Close',
  open: 'Open',
  copy: 'Copy',
  download: 'Download',
  upload: 'Upload',

  // States
  loading: 'Loading...',
  noData: 'No data',
  never: 'Never',
  yes: 'Yes',
  no: 'No',
  all: 'All',
  none: 'None',
  any: 'Any',

  // Labels
  actions: {
    label: 'Actions',
    toggleLanguage: 'Toggle Language',
  },
  status: 'Status',
  details: 'Details',
  search: 'Search',
  filter: 'Filter',
  sort: 'Sort',
  export: 'Export',
  refresh: 'Refresh',
  retry: 'Retry',
  select: 'Select',
  clear: 'Clear',
  name: 'Name',
  title: 'Title',
  description: 'Description',
  type: 'Type',
  created: 'Created',
  updated: 'Updated',
  id: 'ID',

  // Time
  today: 'Today',
  yesterday: 'Yesterday',
  thisWeek: 'This week',
  lastWeek: 'Last week',
  minutes: 'minutes',

  // Additional labels
  reportedBy: 'Reported by',
  reportedAt: 'Reported at',
  submissions: 'Submissions',
  solutions: 'Solutions',
  page: 'Page',
  saving: 'Saving...',
  premium: 'Premium',
  unpublished: 'Unpublished',
  deleteConfirm: 'Confirm Delete',
  clearSelection: 'Clear Selection',
  reasonLabel: 'Reason',
  reasonPlaceholder: 'Please provide a reason...',
  noDataAvailable: 'No data available',

  // Form field annotations (TestCaseForm)
  optional: 'optional',

  // Flag action (EntityActionDialog)
  flag: 'Flag',
  flagConfirm: 'Flag',
  flagDescription: 'Flag this content for moderator review.',
  flagSuccess: 'Content flagged successfully',
  flagError: 'Failed to flag content',

  // Delete action (EntityActionDialog)
  deleteDescription: 'Are you sure you want to delete this item? This action cannot be undone.',
  deleteDescriptionWithName:
    'Are you sure you want to delete "{name}"? This action cannot be undone.',
  deleteSuccess: 'Deleted successfully',
  deleteError: 'Failed to delete',

  // Reason validation (EntityActionDialog)
  reasonRequired: 'A reason is required',

  // Theme mode labels (used by AuthThemeToggle + settings page)
  appearance: {
    light: 'Light',
    dark: 'Dark',
    system: 'System',
  },
} as const
