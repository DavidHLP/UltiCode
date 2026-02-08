/**
 * Core User entity type
 * Shared across backend, console, and management
 */

import type { UserRole } from '../enums/role.enum'

export interface UserEntity {
  id: string
  username: string
  email: string
  name: string
  avatar?: string
  bio?: string
  role: UserRole
  is_active: boolean
  is_banned: boolean
  created_at: string
  updated_at: string
}

export { UserRole } from '../enums/role.enum'
