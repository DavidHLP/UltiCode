export default {
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

  // Validation
  validation: {
    username: {
      required: "Username is required",
      minLength: "Username must be at least 3 characters",
      maxLength: "Username must be at most 20 characters",
      format: "Username can only contain letters, numbers, and underscores",
    },
    email: {
      required: "Email is required",
      invalid: "Invalid email format",
    },
    password: {
      required: "Password is required",
      minLength: "Password must be at least 8 characters",
      uppercase: "Password must contain at least one uppercase letter",
      lowercase: "Password must contain at least one lowercase letter",
      number: "Password must contain at least one number",
    },
    confirmPassword: {
      required: "Please confirm your password",
      mismatch: "Passwords do not match",
    },
  },
} as const;
