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
 * in {@code services}; the value must stay byte-identical or saved
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

// ============================================================================
// Declarative snake/camel boundary.
//
// Each category declares a single { wireKey: dtoKey } table; the generic
// toWire/fromWire pair below walks the table to rename keys. Adding a field
// to a category is now one line in its table instead of a freshly
// hand-written adapter pair. The tables are the only place the snake/camel
// pairing is spelled out.
//
// `toWire` only emits keys whose dto value is defined (PATCH semantics —
// partial slices must not carry undefined wire keys). `fromWire` emits every
// mapped key (GET responses always carry the full category slice).
// ============================================================================

type FieldMapping<W extends string, D extends string> = {
  readonly [K in W]: D
}

const GENERAL_MAPPING = {
  maintenanceMode: 'maintenance_mode',
  maintenanceMessage: 'maintenance_message',
  enableRegistrations: 'enable_registrations',
  siteName: 'site_name',
  siteDescription: 'site_description',
  requireEmailVerification: 'require_email_verification',
} as const satisfies FieldMapping<keyof GeneralWire & string, keyof SystemSettings & string>

const EMAIL_MAPPING = {
  smtpHost: 'smtp_host',
  smtpPort: 'smtp_port',
  smtpUser: 'smtp_user',
  smtpPassword: 'smtp_password',
  smtpFrom: 'smtp_from',
  smtpFromName: 'smtp_from_name',
  smtpSecure: 'smtp_secure',
} as const satisfies FieldMapping<keyof EmailWire & string, keyof EmailSettings & string>

const RATE_LIMIT_MAPPING = {
  rateLimitApi: 'rate_limit_api',
  rateLimitSubmission: 'rate_limit_submission',
  rateLimitAuth: 'rate_limit_auth',
  rateLimitUpload: 'rate_limit_upload',
} as const satisfies FieldMapping<keyof RateLimitWire & string, keyof RateLimitSettings & string>

const UPLOAD_MAPPING = {
  uploadMaxSize: 'upload_max_size',
  uploadAllowedTypes: 'upload_allowed_types',
  uploadMaxFiles: 'upload_max_files',
} as const satisfies FieldMapping<keyof UploadWire & string, keyof UploadSettings & string>

const FEATURE_MAPPING = {
  featureContest: 'feature_contest',
  featureForum: 'feature_forum',
  featureSolutions: 'feature_solutions',
  featureSubscriptions: 'feature_subscriptions',
  featureAchievements: 'feature_achievements',
  featureNotifications: 'feature_notifications',
  featureBookmarks: 'feature_bookmarks',
  featureProblemLists: 'feature_problem_lists',
} as const satisfies FieldMapping<keyof FeatureWire & string, keyof FeatureToggles & string>

/**
 * Rename a partial dto's snake_case keys to camelCase wire keys, dropping
 * any key whose value is undefined. The mapping's wireKey and dtoKey are
 * constrained to `keyof W` and `keyof D` respectively so a misspelled
 * mapping entry fails to compile rather than silently dropping the field
 * at runtime. The final `as Partial<W>` / `as D` is required because
 * TypeScript cannot prove that the value copied from a string-keyed
 * source object matches the target's element type, but every key was
 * already validated by the generic constraint.
 */
function toWire<W extends object, D extends object>(
  dto: Partial<D>,
  mapping: Readonly<Record<Extract<keyof W, string>, Extract<keyof D, string>>>,
): Partial<W> {
  const source = dto as Record<string, unknown>
  const out: Record<string, unknown> = {}
  for (const entry of Object.entries(mapping) as Array<[string, string]>) {
    const wireKey = entry[0]
    const dtoKey = entry[1]
    const value = source[dtoKey]
    if (value !== undefined) out[wireKey] = value
  }
  return out as Partial<W>
}

/** Rename a full wire object's camelCase keys to snake_case dto keys. */
function fromWire<W extends object, D extends object>(
  wire: W,
  mapping: Readonly<Record<Extract<keyof W, string>, Extract<keyof D, string>>>,
): D {
  const source = wire as Record<string, unknown>
  const out: Record<string, unknown> = {}
  for (const entry of Object.entries(mapping) as Array<[string, string]>) {
    const wireKey = entry[0]
    const dtoKey = entry[1]
    out[dtoKey] = source[wireKey]
  }
  return out as D
}

// Per-category bindings so call sites stay one-word. Each closing arrow
// carries its category's mapping so the settingsApi object reads as before.

const generalToWire = (dto: Partial<SystemSettings>): Partial<GeneralWire> =>
  toWire<GeneralWire, SystemSettings>(dto, GENERAL_MAPPING)
const generalFromWire = (wire: GeneralWire): SystemSettings =>
  fromWire<GeneralWire, SystemSettings>(wire, GENERAL_MAPPING)

/**
 * Email wire adapter. The password field carries the backend's
 * {@link SMTP_PASSWORD_MASK} sentinel unless the admin typed a new value;
 * sending the mask (or null) on PATCH tells the backend to preserve the
 * stored secret. We collapse empty/undefined to the mask as well so a
 * half-cleared form can never wipe the credential.
 */
function emailToWire(dto: Partial<EmailSettings>): Partial<EmailWire> {
  const out = toWire<EmailWire, EmailSettings>(dto, EMAIL_MAPPING)
  if (out.smtpPassword !== undefined) {
    out.smtpPassword =
      out.smtpPassword && out.smtpPassword !== SMTP_PASSWORD_MASK
        ? out.smtpPassword
        : SMTP_PASSWORD_MASK
  }
  return out
}
const emailFromWire = (wire: EmailWire): EmailSettings =>
  fromWire<EmailWire, EmailSettings>(wire, EMAIL_MAPPING)

const rateLimitToWire = (dto: Partial<RateLimitSettings>): Partial<RateLimitWire> =>
  toWire<RateLimitWire, RateLimitSettings>(dto, RATE_LIMIT_MAPPING)
const rateLimitFromWire = (wire: RateLimitWire): RateLimitSettings =>
  fromWire<RateLimitWire, RateLimitSettings>(wire, RATE_LIMIT_MAPPING)

const uploadToWire = (dto: Partial<UploadSettings>): Partial<UploadWire> =>
  toWire<UploadWire, UploadSettings>(dto, UPLOAD_MAPPING)
const uploadFromWire = (wire: UploadWire): UploadSettings =>
  fromWire<UploadWire, UploadSettings>(wire, UPLOAD_MAPPING)

const featureToWire = (dto: Partial<FeatureToggles>): Partial<FeatureWire> =>
  toWire<FeatureWire, FeatureToggles>(dto, FEATURE_MAPPING)
const featureFromWire = (wire: FeatureWire): FeatureToggles =>
  fromWire<FeatureWire, FeatureToggles>(wire, FEATURE_MAPPING)

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
