import { apiGet, apiPost, apiPatch } from '@/utils/request'

// ============================================================================
// Enums (synced with backend DTOs)
// ============================================================================

export enum ReportCategory {
  SPAM = 'SPAM',
  HARASSMENT = 'HARASSMENT',
  HATE_SPEECH = 'HATE_SPEECH',
  VIOLENCE = 'VIOLENCE',
  SEXUAL_CONTENT = 'SEXUAL_CONTENT',
  MISINFORMATION = 'MISINFORMATION',
  WRONG_ANSWER = 'WRONG_ANSWER',
  COPYRIGHT = 'COPYRIGHT',
  OTHER = 'OTHER',
}

export enum ModerationActionType {
  DELETED = 'DELETED',
  HIDDEN = 'HIDDEN',
  RESTORED = 'RESTORED',
  WARNED = 'WARNED',
  TEMP_BANNED = 'TEMP_BANNED',
  PERM_BANNED = 'PERM_BANNED',
  DISMISSED = 'DISMISSED',
  RESOLVED = 'RESOLVED',
  APPEAL_PENDING = 'APPEAL_PENDING',
  APPEAL_APPROVED = 'APPEAL_APPROVED',
  APPEAL_REJECTED = 'APPEAL_REJECTED',
}

export enum ModerationStatus {
  PENDING = 'PENDING',
  UNDER_REVIEW = 'UNDER_REVIEW',
  RESOLVED = 'RESOLVED',
  DISMISSED = 'DISMISSED',
  APPEAL_PENDING = 'APPEAL_PENDING',
}

export enum ReportStatus {
  PENDING = 'PENDING',
  REVIEWED = 'REVIEWED',
  RESOLVED = 'RESOLVED',
  DISMISSED = 'DISMISSED',
}

export enum AppealStatus {
  PENDING = 'PENDING',
  UNDER_REVIEW = 'UNDER_REVIEW',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
}

export type ModeratableEntityType =
  | 'forum_post'
  | 'forum_comment'
  | 'solution'
  | 'solution_comment'
  | 'problem'

// ============================================================================
// Types
// ============================================================================

export interface ModerationQueueItem {
  id: string
  entity_type: ModeratableEntityType
  entity_id: string
  status: ModerationStatus
  priority: number
  primary_category: ReportCategory
  assigned_to_id?: string
  assigned_to?: {
    id: string
    username: string
    display_name?: string
    avatar_url?: string
  }
  created_at: Date
  updated_at: Date
  report_count: number
  reports?: Report[]
  actions?: ModerationAction[]
}

export interface Report {
  id: string
  reporter_id: string
  reporter?: {
    id: string
    username: string
    display_name?: string
    avatar_url?: string
  }
  entity_type: ModeratableEntityType
  entity_id: string
  category: ReportCategory
  status: ReportStatus
  reason?: string
  evidence?: string
  created_at: Date
  queue_id?: string
}

export interface ModerationAction {
  id: string
  queue_id: string
  action_type: ModerationActionType
  performed_by: string
  performer?: {
    id: string
    username: string
    display_name?: string
    avatar_url?: string
  }
  note?: string
  duration_days?: number
  created_at: Date
}

export interface Appeal {
  id: string
  queue_id: string
  queue?: ModerationQueueItem
  appellant_id: string
  appellant?: {
    id: string
    username: string
    display_name?: string
    avatar_url?: string
  }
  reason: string
  evidence?: string
  status: AppealStatus
  reviewed_by?: string
  reviewer?: {
    id: string
    username: string
    display_name?: string
  }
  response?: string
  created_at: Date
  updated_at: Date
}

export interface ModerationStats {
  total_pending: number
  total_under_review: number
  total_resolved: number
  total_dismissed: number
  total_appeal_pending: number
  by_category: Record<ReportCategory, number>
  by_entity_type: Record<ModeratableEntityType, number>
  avg_resolution_time_hours?: number
}

export interface UserWarning {
  id: string
  user_id: string
  user?: {
    id: string
    username: string
    display_name?: string
  }
  queue_id?: string
  action_id?: string
  reason: string
  category?: ReportCategory
  acknowledged: boolean
  acknowledged_at?: Date
  created_at: Date
  expires_at?: Date
}

export interface UserBan {
  id: string
  user_id: string
  user?: {
    id: string
    username: string
    display_name?: string
  }
  is_permanent: boolean
  reason: string
  category?: ReportCategory
  banned_by: string
  banner?: {
    id: string
    username: string
    display_name?: string
  }
  queue_id?: string
  action_id?: string
  starts_at: Date
  ends_at?: Date
  is_active: boolean
  unban_reason?: string
  unbanned_by?: string
  unbanned_at?: Date
  created_at: Date
}

// ============================================================================
// Query Parameters
// ============================================================================

export interface QueryModerationQueueParams {
  page?: number
  limit?: number
  status?: ModerationStatus
  primary_category?: ReportCategory
  entity_type?: ModeratableEntityType
  assigned_to_id?: string
  min_priority?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface QueryReportsParams {
  page?: number
  limit?: number
  status?: ReportStatus
  category?: ReportCategory
  entity_type?: ModeratableEntityType
  entity_id?: string
  reporter_id?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface QueryAppealsParams {
  page?: number
  limit?: number
  status?: AppealStatus
  queue_id?: string
  appellant_id?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface QueryUserWarningsParams {
  page?: number
  limit?: number
  user_id?: string
  acknowledged?: boolean
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface QueryUserBansParams {
  page?: number
  limit?: number
  user_id?: string
  active?: boolean
  is_permanent?: boolean
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// ============================================================================
// Request DTOs
// ============================================================================

export interface AssignModerationDto {
  assigned_to_id: string
}

export interface PerformModerationActionDto {
  action: ModerationActionType
  note?: string
  duration_days?: number
}

export interface BatchModerationActionDto {
  queue_ids: string[]
  action: ModerationActionType
  note?: string
}

export interface CreateReportDto {
  entity_type: ModeratableEntityType
  entity_id: string
  category: ReportCategory
  reason?: string
  evidence?: string
}

export interface CreateAppealDto {
  queue_id: string
  reason: string
  evidence?: string
}

export interface ReviewAppealDto {
  status: AppealStatus.APPROVED | AppealStatus.REJECTED
  response?: string
}

export interface CreateUserBanDto {
  user_id: string
  is_permanent?: boolean
  reason: string
  category?: ReportCategory
  duration_days?: number
  queue_id?: string
  action_id?: string
}

export interface RevokeBanDto {
  unban_reason: string
}

// ============================================================================
// Response Types
// ============================================================================

export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface BatchActionResult {
  results: Array<{
    id: string
    success: boolean
    error?: string
  }>
}

// ============================================================================
// API Functions
// ============================================================================

export const moderationQueueApi = {
  /**
   * Get moderation queue items with pagination and filters
   */
  async getQueue(
    params: QueryModerationQueueParams = {},
    signal?: AbortSignal,
  ): Promise<PaginatedResponse<ModerationQueueItem>> {
    return apiGet<PaginatedResponse<ModerationQueueItem>>('/moderation/queue', {
      params,
      signal,
    })
  },

  /**
   * Get a single queue item by ID
   */
  async getQueueItem(id: string, signal?: AbortSignal): Promise<ModerationQueueItem> {
    return apiGet<ModerationQueueItem>(`/moderation/queue/${id}`, { signal })
  },

  /**
   * Get queue item by entity
   */
  async getQueueByEntity(
    entityType: ModeratableEntityType,
    entityId: string,
    signal?: AbortSignal,
  ): Promise<ModerationQueueItem | null> {
    return apiGet<ModerationQueueItem | null>(
      `/moderation/queue/entity/${entityType}/${entityId}`,
      { signal },
    )
  },

  /**
   * Get moderation statistics
   */
  async getStats(signal?: AbortSignal): Promise<ModerationStats> {
    return apiGet<ModerationStats>('/moderation/queue/stats', { signal })
  },

  /**
   * Claim a queue item (assign to current user)
   */
  async claimItem(id: string): Promise<ModerationQueueItem> {
    return apiPost<ModerationQueueItem>(`/moderation/queue/${id}/claim`)
  },

  /**
   * Assign a queue item to a specific user
   */
  async assignItem(id: string, data: AssignModerationDto): Promise<ModerationQueueItem> {
    return apiPost<ModerationQueueItem>(`/moderation/queue/${id}/assign`, data)
  },

  /**
   * Unassign a queue item
   */
  async unassignItem(id: string): Promise<ModerationQueueItem> {
    return apiPatch<ModerationQueueItem>(`/moderation/queue/${id}/unassign`)
  },

  /**
   * Perform a moderation action on a queue item
   */
  async performAction(
    id: string,
    data: PerformModerationActionDto,
  ): Promise<ModerationQueueItem> {
    return apiPost<ModerationQueueItem>(`/moderation/queue/${id}/action`, data)
  },

  /**
   * Perform batch moderation action on multiple queue items
   */
  async batchAction(data: BatchModerationActionDto): Promise<BatchActionResult> {
    return apiPost<BatchActionResult>('/moderation/queue/batch-action', data)
  },
}

export const reportsApi = {
  /**
   * Get reports with pagination and filters
   */
  async getReports(
    params: QueryReportsParams = {},
    signal?: AbortSignal,
  ): Promise<PaginatedResponse<Report>> {
    return apiGet<PaginatedResponse<Report>>('/reports', { params, signal })
  },

  /**
   * Get a single report by ID
   */
  async getReport(id: string, signal?: AbortSignal): Promise<Report> {
    return apiGet<Report>(`/reports/${id}`, { signal })
  },

  /**
   * Get reports for a specific entity
   */
  async getReportsByEntity(
    entityType: ModeratableEntityType,
    entityId: string,
    signal?: AbortSignal,
  ): Promise<Report[]> {
    return apiGet<Report[]>(`/reports/entity/${entityType}/${entityId}`, { signal })
  },

  /**
   * Create a new report (for regular users)
   */
  async createReport(data: CreateReportDto): Promise<Report> {
    return apiPost<Report>('/reports', data)
  },
}

export const appealsApi = {
  /**
   * Get appeals with pagination and filters
   */
  async getAppeals(
    params: QueryAppealsParams = {},
    signal?: AbortSignal,
  ): Promise<PaginatedResponse<Appeal>> {
    return apiGet<PaginatedResponse<Appeal>>('/appeals', { params, signal })
  },

  /**
   * Get current user's appeals
   */
  async getMyAppeals(signal?: AbortSignal): Promise<Appeal[]> {
    return apiGet<Appeal[]>('/appeals/my', { signal })
  },

  /**
   * Get a single appeal by ID
   */
  async getAppeal(id: string, signal?: AbortSignal): Promise<Appeal> {
    return apiGet<Appeal>(`/appeals/${id}`, { signal })
  },

  /**
   * Get appeal statistics
   */
  async getStats(signal?: AbortSignal): Promise<{
    total_pending: number
    total_under_review: number
    total_approved: number
    total_rejected: number
    avg_review_time_hours?: number
  }> {
    return apiGet('/appeals/stats', { signal })
  },

  /**
   * Create a new appeal (for regular users)
   */
  async createAppeal(data: CreateAppealDto): Promise<Appeal> {
    return apiPost<Appeal>('/appeals', data)
  },

  /**
   * Review an appeal (admin only)
   */
  async reviewAppeal(id: string, data: ReviewAppealDto): Promise<Appeal> {
    return apiPatch<Appeal>(`/appeals/${id}/review`, data)
  },
}

// Combined export for convenience
export const moderationApi = {
  queue: moderationQueueApi,
  reports: reportsApi,
  appeals: appealsApi,
}

export default moderationApi
