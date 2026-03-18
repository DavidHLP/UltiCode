export default {
  title: 'System Settings',
  description: 'Manage system configuration',

  // Tabs
  tabs: {
    general: 'General',
    security: 'Security',
    notifications: 'Notifications',
    appearance: 'Appearance',
    language: 'Language',
    advanced: 'Advanced',
  },

  // General settings
  general: {
    siteName: 'Site Name',
    siteDescription: 'Site Description',
    siteUrl: 'Site URL',
    contactEmail: 'Contact Email',
    timezone: 'Timezone',
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
    cacheCleared: 'Cache cleared successfully',
  },

  // Buttons
  buttons: {
    save: 'Save Settings',
    reset: 'Reset',
    testEmail: 'Test Email',
    clearCache: 'Clear Cache',
  },
} as const
