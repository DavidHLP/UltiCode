import { apiGet, apiPatch, apiPost } from '@/utils/request'

export interface SystemSettings {
  maintenance_mode: boolean
  maintenance_message: string
  enable_registrations: boolean
  site_name: string
  site_description: string
  require_email_verification: boolean
}

export interface MaintenanceModeDto {
  enabled: boolean
  message?: string
}

export const settingsApi = {
  async getSettings(): Promise<SystemSettings> {
    const response = await apiGet<SystemSettings>('/admin/settings')
    return response
  },

  async updateSettings(
    data: Partial<SystemSettings>,
  ): Promise<{ message: string; settings: SystemSettings }> {
    const response = await apiPatch<{ message: string; settings: SystemSettings }>(
      '/admin/settings',
      data,
    )
    return response
  },

  async toggleMaintenance(
    data: MaintenanceModeDto,
  ): Promise<{ message: string; maintenance_mode: boolean }> {
    const response = await apiPost<{ message: string; maintenance_mode: boolean }>(
      '/admin/settings/maintenance',
      data,
    )
    return response
  },

  async clearCache(): Promise<{ message: string }> {
    const response = await apiPost<{ message: string }>('/admin/settings/cache/clear')
    return response
  },
}
