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

// Legacy interface for backward compatibility
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

export const settingsApi = {
  // Legacy method - returns general settings only
  async getSettings(): Promise<SystemSettings> {
    const response = await apiGet<SystemSettings>('/admin/settings')
    return response
  },

  // Get all settings
  async getAllSettings(): Promise<AllSettings> {
    const response = await apiGet<AllSettings>('/admin/settings/all')
    return response
  },

  // Get email settings
  async getEmailSettings(): Promise<EmailSettings> {
    const response = await apiGet<EmailSettings>('/admin/settings/email')
    return response
  },

  // Get rate limit settings
  async getRateLimitSettings(): Promise<RateLimitSettings> {
    const response = await apiGet<RateLimitSettings>('/admin/settings/rate-limits')
    return response
  },

  // Get upload settings
  async getUploadSettings(): Promise<UploadSettings> {
    const response = await apiGet<UploadSettings>('/admin/settings/uploads')
    return response
  },

  // Get feature toggles
  async getFeatureToggles(): Promise<FeatureToggles> {
    const response = await apiGet<FeatureToggles>('/admin/settings/features')
    return response
  },

  // Update all settings
  async updateSettings(
    data: Partial<AllSettings>,
  ): Promise<{ message: string; settings: AllSettings }> {
    const response = await apiPatch<{ message: string; settings: AllSettings }>(
      '/admin/settings',
      data,
    )
    return response
  },

  // Update email settings
  async updateEmailSettings(
    data: Partial<EmailSettings>,
  ): Promise<{ message: string; settings: AllSettings }> {
    const response = await apiPatch<{ message: string; settings: AllSettings }>(
      '/admin/settings/email',
      data,
    )
    return response
  },

  // Update rate limit settings
  async updateRateLimitSettings(
    data: Partial<RateLimitSettings>,
  ): Promise<{ message: string; settings: AllSettings }> {
    const response = await apiPatch<{ message: string; settings: AllSettings }>(
      '/admin/settings/rate-limits',
      data,
    )
    return response
  },

  // Update upload settings
  async updateUploadSettings(
    data: Partial<UploadSettings>,
  ): Promise<{ message: string; settings: AllSettings }> {
    const response = await apiPatch<{ message: string; settings: AllSettings }>(
      '/admin/settings/uploads',
      data,
    )
    return response
  },

  // Update feature toggles
  async updateFeatureToggles(
    data: Partial<FeatureToggles>,
  ): Promise<{ message: string; settings: AllSettings }> {
    const response = await apiPatch<{ message: string; settings: AllSettings }>(
      '/admin/settings/features',
      data,
    )
    return response
  },

  // Toggle maintenance mode
  async toggleMaintenance(
    data: MaintenanceModeDto,
  ): Promise<{ message: string; maintenance_mode: boolean }> {
    const response = await apiPost<{ message: string; maintenance_mode: boolean }>(
      '/admin/settings/maintenance',
      data,
    )
    return response
  },

  // Clear cache
  async clearCache(): Promise<{ message: string }> {
    const response = await apiPost<{ message: string }>('/admin/settings/cache/clear')
    return response
  },

  // Reset to defaults
  async resetToDefaults(): Promise<{ message: string; settings: AllSettings }> {
    const response = await apiPost<{ message: string; settings: AllSettings }>(
      '/admin/settings/reset',
    )
    return response
  },
}
