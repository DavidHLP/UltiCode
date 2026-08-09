export default {
  // Accessibility
  skipToContent: "Skip to main content",

  // Common actions
  actions: {
    save: "Save",
    cancel: "Cancel",
    delete: "Delete",
    edit: "Edit",
    create: "Create",
    submit: "Submit",
    confirm: "Confirm",
    search: "Search",
    filter: "Filter",
    sort: "Sort",
    refresh: "Refresh",
    loadMore: "Load More",
    copyLink: "Copy Link",
    share: "Share",
    reply: "Reply",
    close: "Close",
    back: "Back",
    next: "Next",
    previous: "Previous",
    reset: "Reset",
    apply: "Apply",
    clear: "Clear",
    view: "View",
    download: "Download",
    upload: "Upload",
    add: "Add",
    remove: "Remove",
    update: "Update",
    copy: "Copy",
    paste: "Paste",
    retry: "Retry",
    toggleLanguage: "Toggle Language",
    selectAll: "Select All",
  },

  // Months
  months: {
    jan: "Jan",
    feb: "Feb",
    mar: "Mar",
    apr: "Apr",
    may: "May",
    jun: "Jun",
    jul: "Jul",
    aug: "Aug",
    sep: "Sep",
    oct: "Oct",
    nov: "Nov",
    dec: "Dec",
  },

  // Common status
  status: {
    loading: "Loading...",
    success: "Success",
    error: "Error",
    pending: "Pending",
    empty: "No Data",
    notFound: "Not Found",
    saving: "Saving...",
    saved: "Saved",
    processing: "Processing...",
    completed: "Completed",
    failed: "Failed",
  },

  // Time related
  time: {
    now: "Just now",
    today: "Today",
    yesterday: "Yesterday",
    earlier: "Earlier",
    minutesAgo: "{n} minutes ago",
    hoursAgo: "{n} hours ago",
    daysAgo: "{n} days ago",
    weeksAgo: "{n} weeks ago",
    monthsAgo: "{n} months ago",
    yearsAgo: "{n} years ago",
  },

  // Common labels
  labels: {
    all: "All",
    none: "None",
    yes: "Yes",
    no: "No",
    on: "On",
    off: "Off",
    enabled: "Enabled",
    disabled: "Disabled",
    required: "Required",
    optional: "Optional",
    default: "Default",
    custom: "Custom",
    less: "Less",
    more: "More",
    example: "Example",
    explanation: "Explanation",
    soon: "Soon",
    guest: "Guest",
    admin: "Admin",
    new: "New",
    name: "Name",
    selectTab: "Select tab",
  },

  // Pagination
  pagination: {
    page: "Page {current} of {total}",
    items: "{total} items",
    itemsPerPage: "{count} per page",
    goToPage: "Go to",
    firstPage: "First page",
    lastPage: "Last page",
    previousPage: "Previous page",
    nextPage: "Next page",
  },

  // Confirmation dialogs
  confirm: {
    title: "Confirm",
    deleteTitle: "Confirm Delete",
    deleteMessage:
      "Are you sure you want to delete this? This action cannot be undone.",
    unsavedChanges: "You have unsaved changes. Are you sure you want to leave?",
  },

  // Messages
  messages: {
    operationSuccess: "Operation successful",
    operationFailed: "Operation failed",
    copiedToClipboard: "Copied to clipboard",
    copyFailed: "Copy failed",
    networkError: "Network connection failed. Please try again later.",
    serverError: "Server error. Please try again later.",
  },

  // Storage
  storage: {
    localStorageFailed:
      "Language preference will not persist after closing browser",
    sessionStorageFallback:
      "Language preference will persist during this session only",
    memoryStorageFallback: "Language preference set for current page only",
    storageRecovered: "Language preference will now persist normally",
  },

  // Network status
  network: {
    online: "Online",
    offline: "You are currently offline",
    offlineFor: "offline for {duration}",
    reconnect: "Reconnect",
    backOnline: "You are back online",
    connectionLost: "Connection lost",
    connectionRestored: "Connection restored",
  },

  // Error handling
  error: {
    title: "Something went wrong",
    boundaryMessage:
      "An error occurred while rendering this component. Please try again.",
    default: "An unexpected error occurred",
    showDetails: "Show details",
    hideDetails: "Hide details",
    retryMessage:
      "Please try again or contact support if the problem persists.",
    notFound: "Page not found",
    forbidden: "Access denied",
    unauthorized: "Please log in to continue",
    serverError: "Server error. Please try again later.",
    networkError: "Network error. Please check your connection.",
    timeout: "Request timed out. Please try again.",
  },

  // Global search
  search: {
    placeholder: "Search problems, users, posts...",
    noResults: 'No results found for "{query}"',
    startTyping: "Start typing to search...",
    openSearchTip: "to open search",
    resultsCount: "{total} results",
    navigateTip: "to navigate",
    selectTip: "to select",
    types: {
      problem: "Problem",
      user: "User",
      post: "Post",
      solution: "Solution",
      contest: "Contest",
    },
  },

  // PWA
  pwa: {
    updateAvailable: "Update available",
    updateDescription: "A new version is ready to install.",
    update: "Update",
    offlineReady: "App is ready to work offline",
    installPrompt: "Install app for a better experience",
    install: "Install",
    syncing: "Syncing...",
    syncComplete: "Successfully synced {count} submission(s)",
    syncFailed: "Failed to sync submissions",
    queuedSubmissions: "{count} submission(s) queued",
  },

  // Appearance
  appearance: {
    theme: "Theme",
    light: "Light",
    dark: "Dark",
    system: "System",
  },

  // Dismiss button
  dismiss: "Dismiss",
} as const;
