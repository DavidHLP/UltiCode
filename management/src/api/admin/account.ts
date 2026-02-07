import { apiGet, apiPatch, apiPost } from '@/utils/request'
import { toast } from 'vue-sonner'

export interface AccountProfile {
  id: string
  username: string
  name: string
  email: string
  avatar?: string
  bio?: string
  company?: string
  github?: string
  website?: string
  location?: string
  twitter?: string
  preferred_language?: string
  role: string
  joined_at: string
  last_login_at?: string
}

export interface UpdateProfileDto {
  name?: string
  email?: string
  avatar?: string
  bio?: string
  company?: string
  github?: string
  website?: string
  location?: string
  twitter?: string
  preferred_language?: string
}

export interface ChangePasswordDto {
  currentPassword: string
  newPassword: string
}

export interface Subscription {
  id: string
  plan: 'FREE' | 'PREMIUM_MONTHLY' | 'PREMIUM_YEARLY'
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED' | 'PENDING'
  started_at: string
  expires_at?: string
  cancelled_at?: string
}

export const accountApi = {
  async getProfile(): Promise<AccountProfile> {
    const response = await apiGet<AccountProfile>('/admin/account/profile')
    return response
  },

  async updateProfile(data: UpdateProfileDto): Promise<AccountProfile> {
    const response = await apiPatch<AccountProfile>('/admin/account/profile', data)
    toast.success('Profile updated successfully')
    return response
  },

  async changePassword(data: ChangePasswordDto): Promise<void> {
    await apiPost('/admin/account/change-password', data)
    toast.success('Password changed successfully')
  },

  async getSubscription(): Promise<Subscription> {
    const response = await apiGet<Subscription>('/admin/account/subscription')
    return response
  },
}
