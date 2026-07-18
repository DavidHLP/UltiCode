import { apiGet, apiPatch, apiPost } from '@/utils/request'

// All settings interface
export interface AllSettings {
  // General
  maintenance_mode: boolean
  maintenance_message: string
  enable_registrations: boolean
  site_name: string
  site_description: string
  require_email_verification: boolean

  // Email
  smtp_host: string
  smtp_port: string
  smtp_user: string
  smtp_password: string
  smtp_from: string
  smtp_from_name: string
  smtp_secure: boolean

  // Rate Limits
  rate_limit_api: string
  rate_limit_submission: string
  rate_limit_auth: string
  rate_limit_upload: string

  // Uploads
  upload_max_size: string
  upload_allowed_types: string
  upload_max_files: string

  // Feature Toggles
  feature_contest: boolean
  feature_forum: boolean
  feature_solutions: boolean
  feature_subscriptions: boolean
  feature_achievements: boolean
  feature_notifications: boolean
  feature_bookmarks: boolean
  feature_problem_lists: boolean
}

// General-only slice (the SystemSettings name is kept for backward
// compatibility with components that import it).
export interface SystemSettings {
  maintenance_mode: boolean
  maintenance_message: string
  enable_registrations: boolean
  site_name: string
  site_description: string
  require_email_verification: boolean
}

export interface EmailSettings {
  smtp_host: string
  smtp_port: string
  smtp_user: string
  smtp_password: string
  smtp_from: string
  smtp_from_name: string
  smtp_secure: boolean
}

export interface RateLimitSettings {
  rate_limit_api: string
  rate_limit_submission: string
  rate_limit_auth: string
  rate_limit_upload: string
}

export interface UploadSettings {
  upload_max_size: string
  upload_allowed_types: string
  upload_max_files: string
}

export interface FeatureToggles {
  feature_contest: boolean
  feature_forum: boolean
  feature_solutions: boolean
  feature_subscriptions: boolean
  feature_achievements: boolean
  feature_notifications: boolean
  feature_bookmarks: boolean
  feature_problem_lists: boolean
}

export interface MaintenanceModeDto {
  enabled: boolean
  message?: string
}

/**
 * Password mask the backend substitutes into every GET response that
 * contains an SMTP password, and that it treats as "preserve the stored
 * secret" when received on PATCH. Mirrors {@code EmailSettingsVO.PASSWORD_MASK}
 * in {@code backend-spring}; the value must stay byte-identical or saved
 * SMTP secrets get wiped on the next save.
 */
export const SMTP_PASSWORD_MASK = '***'

// ============================================================================
// Wire (camelCase) shapes — private to this module.
//
// The backend VOs use default Jackson camelCase naming (no
// PropertyNamingStrategy is configured — see JacksonConfig). The frontend
// interfaces above are snake_case to match the existing Vue template field
// accessors. Per the frontend rule, the snake/camel conversion lives at the
// API boundary, not in components or stores, so these wire types and the
// adapters below are not exported.
// ============================================================================

interface GeneralWire {
  maintenanceMode: boolean
  maintenanceMessage: string
  enableRegistrations: boolean
  siteName: string
  siteDescription: string
  requireEmailVerification: boolean
}

interface EmailWire {
  smtpHost: string
  smtpPort: string
  smtpUser: string
  smtpPassword: string
  smtpFrom: string
  smtpFromName: string
  smtpSecure: boolean
}

interface RateLimitWire {
  rateLimitApi: string
  rateLimitSubmission: string
  rateLimitAuth: string
  rateLimitUpload: string
}

interface UploadWire {
  uploadMaxSize: string
  uploadAllowedTypes: string
  uploadMaxFiles: string
}

interface FeatureWire {
  featureContest: boolean
  featureForum: boolean
  featureSolutions: boolean
  featureSubscriptions: boolean
  featureAchievements: boolean
  featureNotifications: boolean
  featureBookmarks: boolean
  featureProblemLists: boolean
}

interface AllSettingsWire extends GeneralWire, EmailWire, RateLimitWire, UploadWire, FeatureWire {}

function generalToWire(s: Partial<SystemSettings>): Partial<GeneralWire> {
  const out: Partial<GeneralWire> = {}
  if (s.maintenance_mode !== undefined) out.maintenanceMode = s.maintenance_mode
  if (s.maintenance_message !== undefined) out.maintenanceMessage = s.maintenance_message
  if (s.enable_registrations !== undefined) out.enableRegistrations = s.enable_registrations
  if (s.site_name !== undefined) out.siteName = s.site_name
  if (s.site_description !== undefined) out.siteDescription = s.site_description
  if (s.require_email_verification !== undefined) {
    out.requireEmailVerification = s.require_email_verification
  }
  return out
}

function generalFromWire(w: GeneralWire): SystemSettings {
  return {
    maintenance_mode: w.maintenanceMode,
    maintenance_message: w.maintenanceMessage,
    enable_registrations: w.enableRegistrations,
    site_name: w.siteName,
    site_description: w.siteDescription,
    require_email_verification: w.requireEmailVerification,
  }
}

/**
 * Email wire adapter. The password field carries the backend's
 * {@link SMTP_PASSWORD_MASK} sentinel unless the admin typed a new value;
 * sending the mask (or null) on PATCH tells the backend to preserve the
 * stored secret. We collapse empty/undefined to the mask as well so a
 * half-cleared form can never wipe the credential.
 */
function emailToWire(s: Partial<EmailSettings>): Partial<EmailWire> {
  const out: Partial<EmailWire> = {}
  if (s.smtp_host !== undefined) out.smtpHost = s.smtp_host
  if (s.smtp_port !== undefined) out.smtpPort = s.smtp_port
  if (s.smtp_user !== undefined) out.smtpUser = s.smtp_user
  if (s.smtp_password !== undefined) {
    out.smtpPassword =
      s.smtp_password && s.smtp_password !== SMTP_PASSWORD_MASK
        ? s.smtp_password
        : SMTP_PASSWORD_MASK
  }
  if (s.smtp_from !== undefined) out.smtpFrom = s.smtp_from
  if (s.smtp_from_name !== undefined) out.smtpFromName = s.smtp_from_name
  if (s.smtp_secure !== undefined) out.smtpSecure = s.smtp_secure
  return out
}

function emailFromWire(w: EmailWire): EmailSettings {
  return {
    smtp_host: w.smtpHost,
    smtp_port: w.smtpPort,
    smtp_user: w.smtpUser,
    smtp_password: w.smtpPassword,
    smtp_from: w.smtpFrom,
    smtp_from_name: w.smtpFromName,
    smtp_secure: w.smtpSecure,
  }
}

function rateLimitToWire(s: Partial<RateLimitSettings>): Partial<RateLimitWire> {
  const out: Partial<RateLimitWire> = {}
  if (s.rate_limit_api !== undefined) out.rateLimitApi = s.rate_limit_api
  if (s.rate_limit_submission !== undefined) out.rateLimitSubmission = s.rate_limit_submission
  if (s.rate_limit_auth !== undefined) out.rateLimitAuth = s.rate_limit_auth
  if (s.rate_limit_upload !== undefined) out.rateLimitUpload = s.rate_limit_upload
  return out
}

function rateLimitFromWire(w: RateLimitWire): RateLimitSettings {
  return {
    rate_limit_api: w.rateLimitApi,
    rate_limit_submission: w.rateLimitSubmission,
    rate_limit_auth: w.rateLimitAuth,
    rate_limit_upload: w.rateLimitUpload,
  }
}

function uploadToWire(s: Partial<UploadSettings>): Partial<UploadWire> {
  const out: Partial<UploadWire> = {}
  if (s.upload_max_size !== undefined) out.uploadMaxSize = s.upload_max_size
  if (s.upload_allowed_types !== undefined) out.uploadAllowedTypes = s.upload_allowed_types
  if (s.upload_max_files !== undefined) out.uploadMaxFiles = s.upload_max_files
  return out
}

function uploadFromWire(w: UploadWire): UploadSettings {
  return {
    upload_max_size: w.uploadMaxSize,
    upload_allowed_types: w.uploadAllowedTypes,
    upload_max_files: w.uploadMaxFiles,
  }
}

function featureToWire(s: Partial<FeatureToggles>): Partial<FeatureWire> {
  const out: Partial<FeatureWire> = {}
  if (s.feature_contest !== undefined) out.featureContest = s.feature_contest
  if (s.feature_forum !== undefined) out.featureForum = s.feature_forum
  if (s.feature_solutions !== undefined) out.featureSolutions = s.feature_solutions
  if (s.feature_subscriptions !== undefined) out.featureSubscriptions = s.feature_subscriptions
  if (s.feature_achievements !== undefined) out.featureAchievements = s.feature_achievements
  if (s.feature_notifications !== undefined) out.featureNotifications = s.feature_notifications
  if (s.feature_bookmarks !== undefined) out.featureBookmarks = s.feature_bookmarks
  if (s.feature_problem_lists !== undefined) out.featureProblemLists = s.feature_problem_lists
  return out
}

function featureFromWire(w: FeatureWire): FeatureToggles {
  return {
    feature_contest: w.featureContest,
    feature_forum: w.featureForum,
    feature_solutions: w.featureSolutions,
    feature_subscriptions: w.featureSubscriptions,
    feature_achievements: w.featureAchievements,
    feature_notifications: w.featureNotifications,
    feature_bookmarks: w.featureBookmarks,
    feature_problem_lists: w.featureProblemLists,
  }
}

function allFromWire(w: AllSettingsWire): AllSettings {
  return {
    ...generalFromWire(w),
    ...emailFromWire(w),
    ...rateLimitFromWire(w),
    ...uploadFromWire(w),
    ...featureFromWire(w),
  }
}

export const settingsApi = {
  // Legacy method — returns general settings only.
  async getSettings(): Promise<SystemSettings> {
    const response = await apiGet<GeneralWire>('/admin/settings')
    return generalFromWire(response)
  },

  // Get all settings.
  async getAllSettings(): Promise<AllSettings> {
    const response = await apiGet<AllSettingsWire>('/admin/settings/all')
    return allFromWire(response)
  },

  // Get email settings.
  async getEmailSettings(): Promise<EmailSettings> {
    const response = await apiGet<EmailWire>('/admin/settings/email')
    return emailFromWire(response)
  },

  // Get rate limit settings.
  async getRateLimitSettings(): Promise<RateLimitSettings> {
    const response = await apiGet<RateLimitWire>('/admin/settings/rate-limits')
    return rateLimitFromWire(response)
  },

  // Get upload settings.
  async getUploadSettings(): Promise<UploadSettings> {
    const response = await apiGet<UploadWire>('/admin/settings/uploads')
    return uploadFromWire(response)
  },

  // Get feature toggles.
  async getFeatureToggles(): Promise<FeatureToggles> {
    const response = await apiGet<FeatureWire>('/admin/settings/features')
    return featureFromWire(response)
  },

  /**
   * Update general settings. The backend binds {@code GeneralSettingsVO}
   * (camelCase, 6 fields) — sending the full 28-field AllSettings bag here
   * is what dropped every other category's edits on the floor before this
   * client was sliced. Returns the freshly-saved general slice.
   */
  async updateSettings(data: Partial<SystemSettings>): Promise<SystemSettings> {
    const response = await apiPatch<GeneralWire>('/admin/settings', generalToWire(data))
    return generalFromWire(response)
  },

  /**
   * Update email settings. The smtp_password field is normalized to the
   * {@link SMTP_PASSWORD_MASK} sentinel by {@link emailToWire} unless a new
   * cleartext value was supplied, so a re-submitted form cannot wipe the
   * stored secret. Returns the saved slice with the password masked.
   */
  async updateEmailSettings(data: Partial<EmailSettings>): Promise<EmailSettings> {
    const response = await apiPatch<EmailWire>('/admin/settings/email', emailToWire(data))
    return emailFromWire(response)
  },

  // Update rate limit settings.
  async updateRateLimitSettings(data: Partial<RateLimitSettings>): Promise<RateLimitSettings> {
    const response = await apiPatch<RateLimitWire>(
      '/admin/settings/rate-limits',
      rateLimitToWire(data),
    )
    return rateLimitFromWire(response)
  },

  // Update upload settings.
  async updateUploadSettings(data: Partial<UploadSettings>): Promise<UploadSettings> {
    const response = await apiPatch<UploadWire>('/admin/settings/uploads', uploadToWire(data))
    return uploadFromWire(response)
  },

  // Update feature toggles.
  async updateFeatureToggles(data: Partial<FeatureToggles>): Promise<FeatureToggles> {
    const response = await apiPatch<FeatureWire>('/admin/settings/features', featureToWire(data))
    return featureFromWire(response)
  },

  // Toggle maintenance mode.
  async toggleMaintenance(
    data: MaintenanceModeDto,
  ): Promise<{ message: string; maintenance_mode: boolean }> {
    const response = await apiPost<{ message: string; maintenance_mode: boolean }>(
      '/admin/settings/maintenance',
      data,
    )
    return response
  },

  // Clear cache.
  async clearCache(): Promise<{ clearedScopes: string[]; timestamp: string }> {
    const response = await apiPost<{ clearedScopes: string[]; timestamp: string }>(
      '/admin/settings/cache/clear',
    )
    return response
  },

  // Reset to defaults.
  async resetToDefaults(): Promise<AllSettings> {
    const response = await apiPost<AllSettingsWire>('/admin/settings/reset')
    return allFromWire(response)
  },
}
