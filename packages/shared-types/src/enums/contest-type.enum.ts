/**
 * Contest types
 */
export type ContestType = 'PUBLIC' | 'PRIVATE' | 'CONTEST'

export const ContestType = {
  PUBLIC: 'PUBLIC' as ContestType,
  PRIVATE: 'PRIVATE' as ContestType,
  CONTEST: 'CONTEST' as ContestType,
} as const

/**
 * Contest status
 */
export type ContestStatus = 'UPCOMING' | 'ONGOING' | 'FINISHED'

export const ContestStatus = {
  UPCOMING: 'UPCOMING' as ContestStatus,
  ONGOING: 'ONGOING' as ContestStatus,
  FINISHED: 'FINISHED' as ContestStatus,
} as const
