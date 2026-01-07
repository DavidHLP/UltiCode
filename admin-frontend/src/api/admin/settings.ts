import apiClient from '../client'

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
    const response = await apiClient.get<SystemSettings>('/admin/settings')
    return response.data
  },

  async updateSettings(
    data: Partial<SystemSettings>,
  ): Promise<{ message: string; settings: SystemSettings }> {
    const response = await apiClient.patch<{ message: string; settings: SystemSettings }>(
      '/admin/settings',
      data,
    )
    return response.data
  },

  async toggleMaintenance(
    data: MaintenanceModeDto,
  ): Promise<{ message: string; maintenance_mode: boolean }> {
    const response = await apiClient.post<{ message: string; maintenance_mode: boolean }>(
      '/admin/settings/maintenance',
      data,
    )
    return response.data
  },

  async clearCache(): Promise<{ message: string }> {
    const response = await apiClient.post<{ message: string }>('/admin/settings/cache/clear')
    return response.data
  },
}
