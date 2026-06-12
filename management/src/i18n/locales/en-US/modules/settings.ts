export default {
  title: 'System Settings',
  description: 'Manage system configuration',

  // Tabs
  tabs: {
    general: 'General',
    email: 'Email',
    rateLimits: 'Rate Limits',
    uploads: 'Uploads',
    features: 'Features',
    security: 'Security',
    notifications: 'Notifications',
    appearance: 'Appearance',
    language: 'Language',
    advanced: 'Advanced',
  },

  // General settings
  general: {
    title: 'Site Settings',
    description: 'Configure basic site information',
  },

  siteName: 'Site Name',
  siteDescription: 'Site Description',
  siteUrl: 'Site URL',
  contactEmail: 'Contact Email',
  timezone: 'Timezone',

  // User Registration
  userRegistration: {
    title: 'User Registration',
    description: 'Control user registration settings',
    enableRegistrations: 'Enable Registrations',
    enableRegistrationsDescription: 'Allow new users to register on the platform',
    requireEmailVerification: 'Require Email Verification',
    requireEmailVerificationDescription:
      'Users must verify their email before accessing the platform',
  },

  // System Status
  systemStatus: {
    title: 'System Status',
    description: 'Control system maintenance mode',
    maintenanceMode: 'Maintenance Mode',
    maintenanceModeDescription: 'Put the site in maintenance mode. Only admins can access.',
    maintenanceMessage: 'Maintenance Message',
  },

  // Email settings
  email: {
    title: 'Email Configuration',
    description: 'Configure SMTP settings for sending emails',
    smtpHost: 'SMTP Host',
    smtpPort: 'SMTP Port',
    smtpUser: 'SMTP Username',
    smtpPassword: 'SMTP Password',
    smtpFrom: 'From Email',
    smtpFromName: 'From Name',
    smtpSecure: 'Use TLS',
    smtpSecureDescription: 'Enable TLS encryption for SMTP connection',
  },

  // Rate Limit settings
  rateLimits: {
    title: 'Rate Limits',
    description: 'Configure API rate limiting settings',
    api: 'API Rate Limit',
    apiDescription: 'Maximum API requests per minute',
    submission: 'Submission Rate Limit',
    submissionDescription: 'Maximum code submissions per minute',
    auth: 'Auth Rate Limit',
    authDescription: 'Maximum authentication attempts per minute',
    upload: 'Upload Rate Limit',
    uploadDescription: 'Maximum file uploads per minute',
  },

  // Upload settings
  uploads: {
    title: 'Upload Settings',
    description: 'Configure file upload limits and allowed types',
    maxSize: 'Maximum File Size',
    maxSizeDescription: 'Maximum size for uploaded files (e.g., 10 MB)',
    maxFiles: 'Maximum Files',
    maxFilesDescription: 'Maximum number of files that can be uploaded at once',
    allowedTypes: 'Allowed File Types',
    allowedTypesDescription: 'Comma-separated list of allowed file extensions',
  },

  // Feature toggles
  features: {
    title: 'Feature Toggles',
    description: 'Enable or disable platform features',
    contest: 'Contests',
    contestDescription: 'Enable contest functionality',
    forum: 'Forum',
    forumDescription: 'Enable community forum',
    solutions: 'Solutions',
    solutionsDescription: 'Enable solution sharing',
    subscriptions: 'Subscriptions',
    subscriptionsDescription: 'Enable subscription plans',
    achievements: 'Achievements',
    achievementsDescription: 'Enable achievement system',
    notifications: 'Notifications',
    notificationsDescription: 'Enable notification system',
    bookmarks: 'Bookmarks',
    bookmarksDescription: 'Enable bookmark functionality',
    problemLists: 'Problem Lists',
    problemListsDescription: 'Enable custom problem lists',
  },

  // Actions
  actions: {
    title: 'Actions',
    clearCache: 'Clear Cache',
    resetToDefaults: 'Reset to Defaults',
    resetConfirmTitle: 'Reset Settings to Defaults?',
    resetConfirmDescription:
      'This will reset all settings to their default values. This action cannot be undone.',
    resetConfirm: 'Reset',
    saveChanges: 'Save Changes',
    saving: 'Saving...',
  },

  // Security settings
  security: {
    passwordPolicy: 'Password Policy',
    twoFactorAuth: 'Two-Factor Auth',
    sessionTimeout: 'Session Timeout',
    maxLoginAttempts: 'Max Login Attempts',
  },

  // Notification settings
  notifications: {
    emailNotifications: 'Email Notifications',
    enableEmail: 'Enable Email Notifications',
    smtpSettings: 'SMTP Settings',
  },

  // Appearance settings
  appearance: {
    theme: 'Theme',
    themeDescription: 'Configure the visual theme of the management interface.',
    light: 'Light',
    dark: 'Dark',
    system: 'System',
    primaryColor: 'Primary Color',
  },

  // Language settings
  language: {
    defaultLanguage: 'Default Language',
    supportedLanguages: 'Supported Languages',
  },

  // Advanced settings
  advanced: {
    maintenanceMode: 'Maintenance Mode',
    debugMode: 'Debug Mode',
    cacheSettings: 'Cache Settings',
    clearCache: 'Clear Cache',
  },

  // Toast messages
  toast: {
    saveSuccess: 'Settings saved successfully',
    saveFailed: 'Failed to save settings',
    loadFailed: 'Failed to load settings',
    cacheCleared: 'Cache cleared successfully',
    clearCacheFailed: 'Failed to clear cache',
    resetFailed: 'Failed to reset settings',
    resetSuccess: 'Settings reset to defaults',
  },

  // Buttons
  buttons: {
    save: 'Save Settings',
    reset: 'Reset',
    testEmail: 'Test Email',
    clearCache: 'Clear Cache',
  },
} as const
