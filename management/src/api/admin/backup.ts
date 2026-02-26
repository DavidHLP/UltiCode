import { apiGet, apiPost, apiDelete, apiDownload } from '@/utils/request'

export type BackupType = 'FULL' | 'PARTIAL'
export type BackupStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'

export interface Backup {
  id: string
  filename: string
  size: number
  type: BackupType
  status: BackupStatus
  created_by: string
  created_at: string
  completed_at: string | null
  error: string | null
  metadata: Record<string, unknown> | null
}

export interface BackupQueryParams {
  status?: BackupStatus
  type?: BackupType
  page?: number
  limit?: number
}

export interface BackupListResponse {
  items: Backup[]
  total: number
  page: number
  limit: number
}

export interface CreateBackupDto {
  type?: BackupType
  description?: string
}

export interface RestoreBackupDto {
  confirm: boolean
}

export const backupApi = {
  async getBackups(params?: BackupQueryParams): Promise<BackupListResponse> {
    return apiGet<BackupListResponse>('/admin/backup', { params })
  },

  async getBackup(id: string): Promise<Backup> {
    return apiGet<Backup>(`/admin/backup/${id}`)
  },

  async createBackup(data: CreateBackupDto): Promise<Backup> {
    return apiPost<Backup>('/admin/backup', data)
  },

  async downloadBackup(id: string): Promise<void> {
    const date = new Date().toISOString().split('T')[0]
    await apiDownload(`/admin/backup/${id}/download`, `backup-${id}-${date}.sql`)
  },

  async restoreBackup(id: string): Promise<{ success: boolean }> {
    return apiPost<{ success: boolean }>(`/admin/backup/${id}/restore`, { confirm: true })
  },

  async deleteBackup(id: string): Promise<{ success: boolean }> {
    return apiDelete<{ success: boolean }>(`/admin/backup/${id}`)
  },
}
