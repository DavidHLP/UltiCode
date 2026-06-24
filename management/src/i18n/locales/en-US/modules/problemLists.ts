export default {
  title: 'Problem List Management',
  createTitle: 'Create Problem List',
  editTitle: 'Edit Problem List',
  addList: 'Add List',
  createList: 'Create List',
  editList: 'Edit List',
  searchPlaceholder: 'Search problem lists...',
  generalInfo: 'General Info',
  problems: 'Problems',
  errorLoading: 'Failed to load list',
  backToLists: 'Back to Lists',

  // ========== Column Definitions ==========
  columns: {
    id: 'ID',
    title: 'Title',
    name: 'Name',
    author: 'Author',
    status: 'Status',
    problemCount: 'Problems',
    problems: 'Problems',
    isPublic: 'Public',
    isFeatured: 'Featured',
    featured: 'Featured',
    visibility: 'Visibility',
    order: 'Order',
    description: 'Description',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
  },

  // ========== Status ==========
  status: {
    all: 'All Status',
    DRAFT: 'Draft',
    PUBLISHED: 'Published',
    ARCHIVED: 'Archived',
    visibility: 'Visibility',
    public: 'Public',
    private: 'Private',
    problems: 'Problems',
    featured: 'Featured',
    total: 'Total',
    saving: 'Saving...',
  },

  // ========== Visibility ==========
  visibility: {
    public: 'Public',
    private: 'Private',
    unlisted: 'Unlisted',
  },

  // ========== Actions ==========
  actions: {
    view: 'View',
    edit: 'Edit',
    delete: 'Delete',
    publish: 'Publish',
    feature: 'Feature',
  },

  // ========== Filters ==========
  filters: {
    type: 'Type',
    allTypes: 'All Types',
    featured: 'Featured',
    standard: 'Standard',
    visibility: 'Visibility',
    allVisibility: 'All Visibility',
    public: 'Public',
    private: 'Private',
  },

  // ========== Form ==========
  form: {
    name: 'Name',
    namePlaceholder: 'Enter list name',
    description: 'Description',
    descriptionPlaceholder: 'Enter list description (optional)',
    isPublic: 'Public',
    isPublicDescription: 'Public lists are visible to all users',
    isFeatured: 'Featured',
    isFeaturedDescription: 'Featured lists are shown on the homepage',
    isFeaturedTooltip:
      'When enabled, this list will be displayed in the featured section on the homepage',
    bannerTag: 'Tag',
    bannerTagPlaceholder: 'e.g., Featured, Popular',
    bannerTagDescription: 'Tag text displayed on the banner',
    bannerTheme: 'Theme',
    bannerThemePlaceholder: 'Select theme color',
    sortOrder: 'Sort Order',
    sortOrderDescription: 'Lower numbers appear first',
    saving: 'Saving...',
    saved: 'Saved',
    saveError: 'Save Failed',
    saveChanges: 'Save Changes',
    creating: 'Creating...',
    createList: 'Create List',
    validation: {
      nameRequired: 'Name is required',
    },
  },

  // ========== Section Titles ==========
  sections: {
    basicInfo: 'Basic Information',
    visibilityFeatured: 'Visibility & Featured',
    bannerSettings: 'Banner Settings',
  },

  // ========== Themes ==========
  themes: {
    blue: 'Blue',
    green: 'Green',
    purple: 'Purple',
    orange: 'Orange',
    red: 'Red',
  },

  // ========== Problems Manager ==========
  problemsManager: {
    manageProblems: 'manage problems',
    problemsCount: '{count} problems',
    addProblem: 'Add Problem',
    saving: 'Saving...',
    saveChanges: 'Save Changes',
    order: 'Order',
    problem: 'Problem',
    difficulty: 'Difficulty',
    noProblems: 'No problems yet. Click "Add Problem" to get started.',
    removeProblem: 'Remove Problem',
  },

  // ========== Delete Dialog ==========
  delete: {
    title: 'Delete List',
    description: 'Are you sure you want to delete the list "{name}"? This action cannot be undone.',
    confirm: 'Confirm Delete',
    cancel: 'Cancel',
    thisList: 'this list',
  },

  // ========== Toast Messages ==========
  toast: {
    loadFailed: 'Failed to load problem lists',
    createSuccess: 'Problem list created successfully',
    createFailed: 'Failed to create problem list',
    updateSuccess: 'Problem list updated successfully',
    updateFailed: 'Failed to update problem list',
    deleteSuccess: 'Problem list deleted successfully',
    deleteFailed: 'Failed to delete problem list',
    problemsUpdated: 'Problems updated successfully',
    problemsUpdateFailed: 'Failed to update problems',
    createdSuccess: 'List created successfully',
    updatedSuccess: 'List updated successfully',
    deletedSuccess: 'List deleted successfully',
    requestCanceled: 'Request cancelled, please retry',
    networkError: 'Network connection failed',
  },

  // ========== Terminal Style ==========
  terminal: {
    total: 'Total',
    featured: 'Featured',
    public: 'Public',
    loading: 'Loading...',
  },

  // ========== Stats ==========
  stats: {
    total: 'Total',
    featured: 'Featured',
    public: 'Public',
    listManagement: 'Problem List Management',
  },

  // ========== Empty State ==========
  empty: {
    title: 'No Lists',
    description: 'Click the button above to create your first list',
  },
} as const
