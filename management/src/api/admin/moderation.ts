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
  entityType: ModeratableEntityType
  entityId: string
  parentId?: string
  status: ModerationStatus
  priority: number
  primaryCategory: ReportCategory

  // Assigned user fields (separated, matching backend VO)
  assignedToId?: string
  assignedToName?: string
  assignedToUsername?: string
  assignedAt?: Date

  // Author fields (separated, matching backend VO)
  authorId?: string
  authorName?: string
  authorUsername?: string

  // Reviewer fields (separated, matching backend VO)
  reviewedById?: string
  reviewedByName?: string
  reviewedAt?: Date

  // Resolution fields
  resolution?: string
  resolutionNote?: string
  resolvedAt?: Date

  createdAt: Date
  updatedAt: Date
  reportCount: number
}

export interface Report {
  id: string

  // Reporter fields (separated, matching backend VO)
  reporterId: string
  reporterName?: string
  reporterUsername?: string

  entityType: ModeratableEntityType
  entityId: string
  category: ReportCategory
  status: ReportStatus
  reason?: string
  evidence?: string
  createdAt: Date
  updatedAt: Date
  queueId?: string
}

export interface ModerationAction {
  id: string
  queueId: string
  action: ModerationActionType
  performedById: string
  performer?: {
    id: string
    username: string
    displayName?: string
    avatarUrl?: string
  }
  note?: string
  durationDays?: number
  createdAt: Date
}

export interface Appeal {
  id: string
  queueId: string

  // Appellant fields (separated, matching backend VO)
  appellantId: string
  appellantName?: string
  appellantUsername?: string

  reason: string
  evidence?: string
  status: AppealStatus

  // Reviewer fields (separated, matching backend VO)
  reviewedById?: string
  reviewedByName?: string
  reviewedAt?: Date

  response?: string
  createdAt: Date
  updatedAt: Date
}

export interface ModerationStats {
  pendingCount: number
  underReviewCount: number
  resolvedCount: number
  dismissedCount: number
  resolvedToday: number
  avgResolutionTimeHours?: number
  pendingAppealsCount: number
  byCategory?: Record<string, number>
  byEntityType?: Record<string, number>
}

// ============================================================================
// Query Parameters
// ============================================================================

export interface QueryModerationQueueParams {
  page?: number
  limit?: number
  status?: ModerationStatus
  primaryCategory?: ReportCategory
  entityType?: ModeratableEntityType
  assignedTo?: string
  minPriority?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface QueryReportsParams {
  page?: number
  limit?: number
  status?: ReportStatus
  category?: ReportCategory
  entityType?: ModeratableEntityType
  entityId?: string
  reporterId?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface QueryAppealsParams {
  page?: number
  limit?: number
  status?: AppealStatus
  queueId?: string
  appellantId?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// ============================================================================
// Request DTOs
// ============================================================================

export interface AssignModerationDto {
  assignedTo: string
}

export interface PerformModerationActionDto {
  action: ModerationActionType
  note?: string
  durationDays?: number
}

export interface BatchModerationActionDto {
  queueIds: string[]
  action: ModerationActionType
  note?: string
  durationDays?: number
}

export interface CreateReportDto {
  entityType: ModeratableEntityType
  entityId: string
  category: ReportCategory
  reason?: string
  evidence?: string
}

export interface CreateAppealDto {
  queueId: string
  reason: string
  evidence?: string
}

/**
 * Body for POST /moderation/appeals/{id}/review.
 *
 * NOTE: Field name is `decision` (NOT `status`).
 * Allowed values: AppealStatus.APPROVED or AppealStatus.REJECTED.
 * `response` is an optional moderator note.
 */
export interface ReviewAppealDto {
  decision: AppealStatus.APPROVED | AppealStatus.REJECTED
  response?: string
}

// ============================================================================
// Response Types
// ============================================================================

export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface BatchActionResult {
  successCount: number
  failureCount: number
  errors: Array<{
    queueId: string
    message: string
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
  async performAction(id: string, data: PerformModerationActionDto): Promise<ModerationQueueItem> {
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
    return apiGet<PaginatedResponse<Report>>('/moderation/reports', { params, signal })
  },

  /**
   * Get a single report by ID
   */
  async getReport(id: string, signal?: AbortSignal): Promise<Report> {
    return apiGet<Report>(`/moderation/reports/${id}`, { signal })
  },

  /**
   * Get reports for a specific entity
   */
  async getReportsByEntity(
    entityType: ModeratableEntityType,
    entityId: string,
    signal?: AbortSignal,
  ): Promise<Report[]> {
    return apiGet<Report[]>(`/moderation/reports/entity/${entityType}/${entityId}`, { signal })
  },

  /**
   * Create a new report (for regular users)
   */
  async createReport(data: CreateReportDto): Promise<Report> {
    return apiPost<Report>('/moderation/reports', data)
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
    return apiGet<PaginatedResponse<Appeal>>('/moderation/appeals', { params, signal })
  },

  /**
   * Get current user's appeals
   */
  async getMyAppeals(signal?: AbortSignal): Promise<Appeal[]> {
    return apiGet<Appeal[]>('/moderation/appeals/my', { signal })
  },

  /**
   * Get a single appeal by ID
   */
  async getAppeal(id: string, signal?: AbortSignal): Promise<Appeal> {
    return apiGet<Appeal>(`/moderation/appeals/${id}`, { signal })
  },

  /**
   * Get appeal statistics
   */
  async getStats(signal?: AbortSignal): Promise<{
    totalPending: number
    totalUnderReview: number
    totalApproved: number
    totalRejected: number
    avgReviewTimeHours?: number
  }> {
    return apiGet('/moderation/appeals/stats', { signal })
  },

  /**
   * Create a new appeal (for regular users)
   */
  async createAppeal(data: CreateAppealDto): Promise<Appeal> {
    return apiPost<Appeal>('/moderation/appeals', data)
  },

  /**
   * Review an appeal (admin only)
   */
  async reviewAppeal(id: string, data: ReviewAppealDto): Promise<Appeal> {
    return apiPost<Appeal>(`/moderation/appeals/${id}/review`, data)
  },
}

// Combined export for convenience
export const moderationApi = {
  queue: moderationQueueApi,
  reports: reportsApi,
  appeals: appealsApi,
}

export default moderationApi
