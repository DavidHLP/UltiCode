import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { apiGet, apiPatch, apiPost } from '@/utils/request'
import { useSystemSettingsStore } from '../system-settings'
import { formatBytes, parseSizeToBytes } from '../system-settings'
import type { AllSettings } from '@/api/admin/settings'

// Keep ApiError (and all other non-mocked exports) so that extractApiErrorMessage's
// `instanceof ApiError` guard works correctly inside store catch blocks.
vi.mock('@/utils/request', async () => {
  const actual = await vi.importActual<typeof import('@/utils/request')>('@/utils/request')
  return {
    ...actual,
    apiGet: vi.fn(),
    apiPatch: vi.fn(),
    apiPost: vi.fn(),
    apiPut: vi.fn(),
    apiDelete: vi.fn(),
    apiUpload: vi.fn(),
    apiDownload: vi.fn(),
  }
})

// Backend serves camelCase (Jackson default — no PropertyNamingStrategy
// configured in JacksonConfig). The API client converts to/from snake_case
// at the boundary, so the store only ever sees snake_case payloads.
const allWire = {
  maintenanceMode: false,
  maintenanceMessage: '',
  enableRegistrations: true,
  siteName: 'UltiCode',
  siteDescription: 'Online Programming Platform',
  requireEmailVerification: false,
  smtpHost: 'smtp.example.com',
  smtpPort: '587',
  smtpUser: 'postmaster',
  smtpPassword: '***',
  smtpFrom: 'noreply@ulticode.com',
  smtpFromName: 'UltiCode',
  smtpSecure: true,
  rateLimitApi: '100',
  rateLimitSubmission: '10',
  rateLimitAuth: '5',
  rateLimitUpload: '20',
  uploadMaxSize: '10485760',
  uploadAllowedTypes: 'jpg,png,pdf',
  uploadMaxFiles: '5',
  featureContest: true,
  featureForum: true,
  featureSolutions: true,
  featureSubscriptions: true,
  featureAchievements: true,
  featureNotifications: true,
  featureBookmarks: true,
  featureProblemLists: true,
}

const expectedAllSnake: AllSettings = {
  maintenance_mode: false,
  maintenance_message: '',
  enable_registrations: true,
  site_name: 'UltiCode',
  site_description: 'Online Programming Platform',
  require_email_verification: false,
  smtp_host: 'smtp.example.com',
  smtp_port: '587',
  smtp_user: 'postmaster',
  smtp_password: '***',
  smtp_from: 'noreply@ulticode.com',
  smtp_from_name: 'UltiCode',
  smtp_secure: true,
  rate_limit_api: '100',
  rate_limit_submission: '10',
  rate_limit_auth: '5',
  rate_limit_upload: '20',
  upload_max_size: '10485760',
  upload_allowed_types: 'jpg,png,pdf',
  upload_max_files: '5',
  feature_contest: true,
  feature_forum: true,
  feature_solutions: true,
  feature_subscriptions: true,
  feature_achievements: true,
  feature_notifications: true,
  feature_bookmarks: true,
  feature_problem_lists: true,
}

describe('useSystemSettingsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(apiGet).mockResolvedValue(allWire)
  })

  describe('load — single owner of AllSettings', () => {
    it('calls GET /admin/settings/all and slices the camelCase payload into snake_case state', async () => {
      const store = useSystemSettingsStore()
      await store.load()

      expect(apiGet).toHaveBeenCalledWith('/admin/settings/all')
      expect(apiGet).toHaveBeenCalledTimes(1)
      // Canonical state is the full bag.
      expect(store.all).toEqual(expectedAllSnake)
      // Slices expose focused views into that bag.
      expect(store.general).toEqual({
        maintenance_mode: false,
        maintenance_message: '',
        enable_registrations: true,
        site_name: 'UltiCode',
        site_description: 'Online Programming Platform',
        require_email_verification: false,
      })
      expect(store.email.smtp_host).toBe('smtp.example.com')
      expect(store.rateLimits.rate_limit_api).toBe('100')
      expect(store.uploads.upload_max_size).toBe('10485760')
      expect(store.features.feature_contest).toBe(true)
    })

    it('clears the dirty flag after a fresh load', async () => {
      const store = useSystemSettingsStore()
      await store.load()
      expect(store.isDirty).toBe(false)
      expect(store.isEmailDirty).toBe(false)
    })
  })

  describe('per-category save routing (the silent-drop fix)', () => {
    it('PATCHes /admin/settings/email — not /admin/settings — when saveEmail runs', async () => {
      vi.mocked(apiPatch).mockResolvedValue({ ...allWire, smtpHost: 'smtp2.example.com' })
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchEmail({ smtp_host: 'smtp2.example.com' })
      await store.saveEmail()

      expect(apiPatch).toHaveBeenCalledTimes(1)
      expect(apiPatch).toHaveBeenCalledWith('/admin/settings/email', expect.anything())
      const [, payload] = vi.mocked(apiPatch).mock.calls[0]
      // Wire payload is camelCase.
      expect(payload).toMatchObject({ smtpHost: 'smtp2.example.com' })
    })

    it('PATCHes /admin/settings/rate-limits for rate-limit saves', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchRateLimits({ rate_limit_api: '200' })
      await store.saveRateLimits()

      expect(apiPatch).toHaveBeenCalledWith('/admin/settings/rate-limits', {
        rateLimitApi: '200',
        rateLimitSubmission: '10',
        rateLimitAuth: '5',
        rateLimitUpload: '20',
      })
    })

    it('PATCHes /admin/settings/uploads for upload saves', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchUploads({ upload_max_files: '10' })
      await store.saveUploads()

      expect(apiPatch).toHaveBeenCalledWith('/admin/settings/uploads', {
        uploadMaxSize: '10485760',
        uploadAllowedTypes: 'jpg,png,pdf',
        uploadMaxFiles: '10',
      })
    })

    it('PATCHes /admin/settings/features for feature saves', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchFeatures({ feature_forum: false })
      await store.saveFeatures()

      expect(apiPatch).toHaveBeenCalledWith('/admin/settings/features', {
        featureContest: true,
        featureForum: false,
        featureSolutions: true,
        featureSubscriptions: true,
        featureAchievements: true,
        featureNotifications: true,
        featureBookmarks: true,
        featureProblemLists: true,
      })
    })

    it('PATCHes /admin/settings (general) for general saves', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchGeneral({ site_name: 'New Name' })
      await store.saveGeneral()

      expect(apiPatch).toHaveBeenCalledWith('/admin/settings', {
        maintenanceMode: false,
        maintenanceMessage: '',
        enableRegistrations: true,
        siteName: 'New Name',
        siteDescription: 'Online Programming Platform',
        requireEmailVerification: false,
      })
    })

    it('saveAllDirty fans out only to dirty categories', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      // Two of five categories touched.
      store.patchEmail({ smtp_host: 'new.example.com' })
      store.patchFeatures({ feature_forum: false })

      await store.saveAllDirty()

      const endpoints = vi
        .mocked(apiPatch)
        .mock.calls.map(([path]) => path)
        .sort()
      expect(endpoints).toEqual(['/admin/settings/email', '/admin/settings/features'])
    })

    it('saveAllDirty is a no-op when nothing is dirty', async () => {
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      await store.saveAllDirty()
      expect(apiPatch).not.toHaveBeenCalled()
    })

    it('does not invoke apiPatch on load (the bug previously POSTed the whole bag)', async () => {
      const store = useSystemSettingsStore()
      await store.load()
      expect(apiPatch).not.toHaveBeenCalled()
      expect(apiPost).not.toHaveBeenCalled()
    })
  })

  describe('SMTP password mask contract', () => {
    it('preserves the "***" mask on the wire when the admin did not type a new password', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      // Edit an unrelated email field; smtp_password still holds the loaded mask.
      store.patchEmail({ smtp_from: 'updated@ulticode.com' })
      await store.saveEmail()

      const [, payload] = vi.mocked(apiPatch).mock.calls[0]
      expect(payload).toMatchObject({ smtpPassword: '***' })
    })

    it('sends the new cleartext password when the admin typed one', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchEmail({ smtp_password: 'new-real-secret' })
      await store.saveEmail()

      const [, payload] = vi.mocked(apiPatch).mock.calls[0]
      expect(payload).toMatchObject({ smtpPassword: 'new-real-secret' })
    })

    it('collapses an empty password field back to the mask so the secret is never wiped', async () => {
      vi.mocked(apiPatch).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      store.patchEmail({ smtp_password: '' })
      await store.saveEmail()

      const [, payload] = vi.mocked(apiPatch).mock.calls[0]
      expect(payload).toMatchObject({ smtpPassword: '***' })
    })
  })

  describe('upload size adapters (1024-base round-trip)', () => {
    it('formats bytes as B / KB / MB / GB with the exact 1024 multiplier', () => {
      expect(formatBytes('0')).toBe('0 B')
      expect(formatBytes('512')).toBe('512 B')
      expect(formatBytes('1024')).toBe('1.0 KB')
      expect(formatBytes('1048576')).toBe('1.0 MB')
      expect(formatBytes('1073741824')).toBe('1.0 GB')
      // The default upload_max_size of 10485760 bytes must render as 10.0 MB.
      expect(formatBytes('10485760')).toBe('10.0 MB')
    })

    it('parses "10 MB" back to exactly 10485760 bytes (no semantic drift)', () => {
      expect(parseSizeToBytes('10 MB')).toBe('10485760')
      expect(parseSizeToBytes('1 KB')).toBe('1024')
      expect(parseSizeToBytes('1 GB')).toBe('1073741824')
      expect(parseSizeToBytes('512')).toBe('512')
    })

    it('round-trips a typed human-readable size through parse → format unchanged', () => {
      const bytes = parseSizeToBytes('10 MB')
      expect(formatBytes(bytes)).toBe('10.0 MB')
    })
  })

  describe('reset — local restore of the last-loaded snapshot', () => {
    it('clears dirty state by restoring every category to the snapshot', async () => {
      const store = useSystemSettingsStore()
      await store.load()

      expect(store.isDirty).toBe(false)
      store.patchEmail({ smtp_host: 'transient.example.com' })
      store.patchGeneral({ site_name: 'Transient' })
      expect(store.isEmailDirty).toBe(true)
      expect(store.isGeneralDirty).toBe(true)
      expect(store.isDirty).toBe(true)

      store.reset()

      expect(store.email.smtp_host).toBe('smtp.example.com')
      expect(store.general.site_name).toBe('UltiCode')
      expect(store.isEmailDirty).toBe(false)
      expect(store.isGeneralDirty).toBe(false)
      expect(store.isDirty).toBe(false)
    })
  })

  describe('resetToDefaultsServer — server-side reset', () => {
    it('POSTs /admin/settings/reset and re-snapshots the returned state', async () => {
      vi.mocked(apiPost).mockResolvedValue(allWire)
      const store = useSystemSettingsStore()
      await store.load()
      // Dirty the local state so we can prove the server response overwrites it.
      store.patchFeatures({ feature_contest: false })
      expect(store.isFeaturesDirty).toBe(true)

      await store.resetToDefaultsServer()

      expect(apiPost).toHaveBeenCalledWith('/admin/settings/reset')
      // and dirty flags clear because the snapshot is refreshed.
      expect(store.features.feature_contest).toBe(true)
      expect(store.isFeaturesDirty).toBe(false)
    })
  })

  describe('saveAllDirty — partial-failure snapshot isolation', () => {
    it('keeps failed category dirty while clearing succeeded category', async () => {
      // email fails (409/conflict); general succeeds and echoes the edit.
      // The bug: snapshot was blindly set to all (which holds the user's
      // failed-email edits), making email appear non-dirty even though it failed.
      vi.mocked(apiPatch).mockImplementation((path: string) => {
        if (path === '/admin/settings/email') {
          return Promise.reject(new Error('email failed — conflict'))
        }
        // Echo the edited general slice back so all.value reflects the persisted value.
        return Promise.resolve({ ...allWire, siteName: 'Dirty Name' })
      })
      const store = useSystemSettingsStore()
      await store.load()
      vi.mocked(apiPatch).mockClear()

      // Dirty both categories.
      store.patchGeneral({ site_name: 'Dirty Name' })
      store.patchEmail({ smtp_host: 'dirty@example.com' })
      expect(store.isGeneralDirty).toBe(true)
      expect(store.isEmailDirty).toBe(true)

      await expect(store.saveAllDirty()).rejects.toThrow()

      // General succeeded — server echoed the edit; snapshot now matches all;
      // dirty is cleared and the persisted value is preserved.
      expect(store.isGeneralDirty).toBe(false)
      expect(store.general.site_name).toBe('Dirty Name')
      // Email failed — snapshot still holds the pre-save email slice,
      // so isEmailDirty stays true (user's edit is still uncommitted).
      expect(store.isEmailDirty).toBe(true)
      // Error is surfaced per-category.
      expect(store.saveStatus.errors.email).toBeTruthy()
    })
  })
})
