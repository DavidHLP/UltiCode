import { apiGet, apiPost } from '@/utils/request'
import {
  csrfManager,
  withBrowserSessionLock,
  type SignedInAccount,
  type LoginResponse,
} from '@ulticode/auth-core'

export const accountSessions = {
  list: () => apiGet<SignedInAccount[]>('/auth/accounts', { skipErrorHandler: true }),
  add(credentials: { username: string; password: string }) {
    return withBrowserSessionLock(async () => {
      const result = await apiPost<LoginResponse>(
        '/auth/accounts',
        { ...credentials, adminOnly: true },
        { skipErrorHandler: true },
      )
      csrfManager.refreshFromResponse(result)
    })
  },
  switchTo(accountId: string) {
    return withBrowserSessionLock(async () => {
      const result = await apiPost<LoginResponse>(
        '/auth/accounts/switch',
        { accountId, adminOnly: true },
        { skipErrorHandler: true },
      )
      csrfManager.refreshFromResponse(result)
    })
  },
}
