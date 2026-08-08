import { apiGet, apiPost, apiPut, apiDelete } from '@/utils/request'

export type EmailStatus = 'PENDING' | 'SENT' | 'FAILED'

export interface EmailTemplate {
  id: string
  name: string
  subject: string
  body: string
  variables?: string[]
  created_at: string
  updated_at: string
}

export interface EmailLog {
  id: string
  template_id: string | null
  recipient: string
  subject: string
  status: EmailStatus
  sent_at: string | null
  error: string | null
  created_at: string
}

export interface EmailQueryParams {
  status?: EmailStatus
  recipient?: string
  page?: number
  limit?: number
}

export interface EmailListResponse {
  items: EmailLog[]
  total: number
  page: number
  limit: number
}

export interface EmailStats {
  total: number
  sent: number
  pending: number
  failed: number
}

export interface SendEmailDto {
  to: string
  subject: string
  html?: string
  text?: string
  templateId?: string
  variables?: Record<string, unknown>
}

export interface CreateTemplateDto {
  name: string
  subject: string
  body: string
  variables?: string[]
}

export interface UpdateTemplateDto {
  name?: string
  subject?: string
  body?: string
  variables?: string[]
}

export const emailApi = {
  // Email sending
  async sendEmail(data: SendEmailDto): Promise<EmailLog> {
    return apiPost<EmailLog>('/admin/email/send', data)
  },

  async getLogs(params?: EmailQueryParams): Promise<EmailListResponse> {
    return apiGet<EmailListResponse>('/admin/email/logs', { params })
  },

  async getStats(): Promise<EmailStats> {
    return apiGet<EmailStats>('/admin/email/stats')
  },

  // Templates
  async getTemplates(): Promise<EmailTemplate[]> {
    return apiGet<EmailTemplate[]>('/admin/email/templates')
  },

  async getTemplate(id: string): Promise<EmailTemplate> {
    return apiGet<EmailTemplate>(`/admin/email/templates/${id}`)
  },

  async createTemplate(data: CreateTemplateDto): Promise<EmailTemplate> {
    return apiPost<EmailTemplate>('/admin/email/templates', data)
  },

  async updateTemplate(id: string, data: UpdateTemplateDto): Promise<EmailTemplate> {
    return apiPut<EmailTemplate>(`/admin/email/templates/${id}`, data)
  },

  async deleteTemplate(id: string): Promise<{ success: boolean }> {
    return apiDelete<{ success: boolean }>(`/admin/email/templates/${id}`)
  },
}
