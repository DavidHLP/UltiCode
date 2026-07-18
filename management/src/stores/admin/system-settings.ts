import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  settingsApi,
  type AllSettings,
  type EmailSettings,
  type FeatureToggles,
  type RateLimitSettings,
  type SystemSettings,
  type UploadSettings,
} from '@/api/admin/settings'
import { extractApiErrorMessage } from '@/utils/error'

/**
 * System settings workspace.
 *
 * <p>Deep-module owner of the administrator settings surface. The five
 * category views ({@code GeneralSettings}, {@code EmailSettings}, …) and
 * {@code SettingsView.vue} consume this store and never touch
 * {@link settingsApi} directly. The workspace owns:
 *
 * <ul>
 *   <li><b>Canonical state</b> &mdash; a single {@link AllSettings} ref
 *       loaded from {@code GET /admin/settings/all}. Category views receive
 *       narrow read-only slices ({@link #general}, {@link #email}, …)
 *       instead of the full 28-field bag.</li>
 *   <li><b>Per-category dirty tracking</b> &mdash; each slice is diffed
 *       against its last-loaded snapshot, so the root "Save Changes" button
 *       can fan out to only the categories that changed.</li>
 *   <li><b>Per-category save routing</b> &mdash; {@link #saveEmail} PATCHes
 *       {@code /admin/settings/email}, {@link #saveGeneral} PATCHes
 *       {@code /admin/settings}, and so on. Before this store existed the
 *       whole bag was POSTed to the general endpoint and every non-general
 *       edit was silently dropped at Jackson binding (the
 *       {@code GeneralSettingsVO} only binds six fields). Routing each
 *       category to its own typed endpoint is the fix.</li>
 *   <li><b>SMTP password mask contract</b> &mdash; the slice always carries
 *       the backend's {@code "***"} sentinel after load; save preserves it
 *       unless the admin typed a new value. See
 *       {@code emailToWire} in {@code settings.ts}.</li>
 *   <li><b>Upload size adapters</b> &mdash; {@link #formatBytes} and
 *       {@link #parseSizeToBytes} move out of {@code UploadSettings.vue}
 *       into this workspace so the byte semantics (1024-base) live next to
 *       the field that owns the wire contract.</li>
 * </ul>
 *
 * <p>Architecture review candidate #3.
 */

// ---------------------------------------------------------------------------
// Upload-size adapters. Exact 1024-base semantics preserved byte-for-byte
// from the original UploadSettings.vue implementation — changing the
// multiplier would silently drift the stored `upload_max_size` value.
// ---------------------------------------------------------------------------

export function formatBytes(bytes: string): string {
  const value = parseInt(bytes, 10)
  if (isNaN(value)) return '0 B'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

export function parseSizeToBytes(sizeStr: string): string {
  const match = sizeStr.match(/^(\d+(?:\.\d+)?)\s*(B|KB|MB|GB)?$/i)
  if (!match || !match[1]) return sizeStr
  const value = parseFloat(match[1])
  const unit = (match[2] || 'B').toUpperCase()
  const multipliers: Record<string, number> = {
    B: 1,
    KB: 1024,
    MB: 1024 * 1024,
    GB: 1024 * 1024 * 1024,
  }
  return String(Math.round(value * (multipliers[unit] || 1)))
}

// ---------------------------------------------------------------------------
// UI placeholder seed for the refs before the first load() resolves.
//
// This is NOT a source of truth. The backend supplies the authoritative
// values via GET /admin/settings/all, and load() overwrites both refs
// before the SettingsView surfaces any component — SettingsView wraps its
// tab content in an opacity-0 container that only flips to opacity-100
// after isLoaded is set in onMounted (after load() resolves), and the
// tab body itself is hidden behind `v-if="loading"` during the fetch.
//
// The values exist solely so the refs carry a complete AllSettings shape
// (TypeScript forbids undefined fields on the typed ref) and so that, if a
// component ever briefly reads a slice before load lands, it sees
// non-surprising empty/conservative defaults rather than broken renders.
// Treat any divergence between these and the backend's defaults as a
// backend-owned correction, not a bug here.
// ---------------------------------------------------------------------------

function defaultAllSettings(): AllSettings {
  return {
    maintenance_mode: false,
    maintenance_message: '',
    enable_registrations: true,
    site_name: '',
    site_description: '',
    require_email_verification: false,
    smtp_host: '',
    smtp_port: '587',
    smtp_user: '',
    smtp_password: '',
    smtp_from: '',
    smtp_from_name: '',
    smtp_secure: true,
    rate_limit_api: '100',
    rate_limit_submission: '10',
    rate_limit_auth: '5',
    rate_limit_upload: '20',
    upload_max_size: '10485760',
    upload_allowed_types: '',
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
}

function sliceGeneral(s: AllSettings): SystemSettings {
  return {
    maintenance_mode: s.maintenance_mode,
    maintenance_message: s.maintenance_message,
    enable_registrations: s.enable_registrations,
    site_name: s.site_name,
    site_description: s.site_description,
    require_email_verification: s.require_email_verification,
  }
}

function sliceEmail(s: AllSettings): EmailSettings {
  return {
    smtp_host: s.smtp_host,
    smtp_port: s.smtp_port,
    smtp_user: s.smtp_user,
    smtp_password: s.smtp_password,
    smtp_from: s.smtp_from,
    smtp_from_name: s.smtp_from_name,
    smtp_secure: s.smtp_secure,
  }
}

function sliceRateLimits(s: AllSettings): RateLimitSettings {
  return {
    rate_limit_api: s.rate_limit_api,
    rate_limit_submission: s.rate_limit_submission,
    rate_limit_auth: s.rate_limit_auth,
    rate_limit_upload: s.rate_limit_upload,
  }
}

function sliceUploads(s: AllSettings): UploadSettings {
  return {
    upload_max_size: s.upload_max_size,
    upload_allowed_types: s.upload_allowed_types,
    upload_max_files: s.upload_max_files,
  }
}

function sliceFeatures(s: AllSettings): FeatureToggles {
  return {
    feature_contest: s.feature_contest,
    feature_forum: s.feature_forum,
    feature_solutions: s.feature_solutions,
    feature_subscriptions: s.feature_subscriptions,
    feature_achievements: s.feature_achievements,
    feature_notifications: s.feature_notifications,
    feature_bookmarks: s.feature_bookmarks,
    feature_problem_lists: s.feature_problem_lists,
  }
}

/** Shallow equality on flat primitive records (every category slice). */
function flatEqual<T>(a: T, b: T): boolean {
  const aRec = a as Record<string, unknown>
  const bRec = b as Record<string, unknown>
  const aKeys = Object.keys(aRec)
  const bKeys = Object.keys(bRec)
  if (aKeys.length !== bKeys.length) return false
  for (const key of aKeys) {
    if (aRec[key] !== bRec[key]) return false
  }
  return true
}

export const useSystemSettingsStore = defineStore('adminSystemSettings', () => {
  const all = ref<AllSettings>(defaultAllSettings())
  const snapshot = ref<AllSettings>(defaultAllSettings())
  const loading = ref(false)
  const saving = ref(false)
  const clearingCache = ref(false)
  const error = ref<string | null>(null)

  // ===== read views (focused slices for each category adapter) =====

  const general = computed<SystemSettings>(() => sliceGeneral(all.value))
  const email = computed<EmailSettings>(() => sliceEmail(all.value))
  const rateLimits = computed<RateLimitSettings>(() => sliceRateLimits(all.value))
  const uploads = computed<UploadSettings>(() => sliceUploads(all.value))
  const features = computed<FeatureToggles>(() => sliceFeatures(all.value))

  // ===== dirty tracking (per-category diff against last-loaded snapshot) =====

  const isGeneralDirty = computed<boolean>(
    () => !flatEqual(general.value, sliceGeneral(snapshot.value)),
  )
  const isEmailDirty = computed<boolean>(() => !flatEqual(email.value, sliceEmail(snapshot.value)))
  const isRateLimitsDirty = computed<boolean>(
    () => !flatEqual(rateLimits.value, sliceRateLimits(snapshot.value)),
  )
  const isUploadsDirty = computed<boolean>(
    () => !flatEqual(uploads.value, sliceUploads(snapshot.value)),
  )
  const isFeaturesDirty = computed<boolean>(
    () => !flatEqual(features.value, sliceFeatures(snapshot.value)),
  )

  const isDirty = computed<boolean>(
    () =>
      isGeneralDirty.value ||
      isEmailDirty.value ||
      isRateLimitsDirty.value ||
      isUploadsDirty.value ||
      isFeaturesDirty.value,
  )

  // ===== per-field patch (components emit a partial slice on edit) =====

  function patchGeneral(patch: Partial<SystemSettings>): void {
    all.value = { ...all.value, ...patch }
  }

  function patchEmail(patch: Partial<EmailSettings>): void {
    all.value = { ...all.value, ...patch }
  }

  function patchRateLimits(patch: Partial<RateLimitSettings>): void {
    all.value = { ...all.value, ...patch }
  }

  function patchUploads(patch: Partial<UploadSettings>): void {
    all.value = { ...all.value, ...patch }
  }

  function patchFeatures(patch: Partial<FeatureToggles>): void {
    all.value = { ...all.value, ...patch }
  }

  // ===== load =====

  async function load(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const data = await settingsApi.getAllSettings()
      all.value = { ...data }
      snapshot.value = { ...data }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to load system settings')
      console.error('Failed to load system settings:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  // ===== per-category save (routes to the correct typed endpoint) =====

  /**
   * Save the general slice. PATCHes {@code /admin/settings} with the
   * 6-field general payload; merging the response back keeps the canonical
   * state in sync with what the server persisted.
   */
  async function saveGeneral(): Promise<void> {
    const result = await settingsApi.updateSettings(general.value)
    all.value = { ...all.value, ...result }
  }

  /**
   * Save the email slice. {@code settingsApi.updateEmailSettings} sends the
   * {@code "***"} password mask unless a new cleartext value was typed, so
   * the stored SMTP secret survives a re-submitted form. The returned
   * slice re-masks the password for re-display.
   */
  async function saveEmail(): Promise<void> {
    const result = await settingsApi.updateEmailSettings(email.value)
    all.value = { ...all.value, ...result }
  }

  async function saveRateLimits(): Promise<void> {
    const result = await settingsApi.updateRateLimitSettings(rateLimits.value)
    all.value = { ...all.value, ...result }
  }

  async function saveUploads(): Promise<void> {
    const result = await settingsApi.updateUploadSettings(uploads.value)
    all.value = { ...all.value, ...result }
  }

  /**
   * Save feature toggles. The backend rejects an all-false PATCH as an
   * accidental empty-request guard, so callers must send the full slice
   * (this store always does). Partial patches would risk tripping the
   * guard.
   */
  async function saveFeatures(): Promise<void> {
    const result = await settingsApi.updateFeatureToggles(features.value)
    all.value = { ...all.value, ...result }
  }

  /**
   * Fan out to whichever categories are dirty. The root "Save Changes"
   * button calls this so a single click still saves every edited tab,
   * while each category lands on its own typed endpoint. After every
   * save resolves, the snapshot is refreshed so dirty flags clear.
   */
  async function saveAllDirty(): Promise<void> {
    if (!isDirty.value) return
    saving.value = true
    error.value = null
    try {
      const tasks: Promise<unknown>[] = []
      if (isGeneralDirty.value) tasks.push(saveGeneral())
      if (isEmailDirty.value) tasks.push(saveEmail())
      if (isRateLimitsDirty.value) tasks.push(saveRateLimits())
      if (isUploadsDirty.value) tasks.push(saveUploads())
      if (isFeaturesDirty.value) tasks.push(saveFeatures())
      await Promise.all(tasks)
      snapshot.value = { ...all.value }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to save system settings')
      console.error('Failed to save system settings:', err)
      throw err
    } finally {
      saving.value = false
    }
  }

  // ===== reset (local restore of last-loaded snapshot) =====

  /**
   * Restore every category to its last-loaded snapshot, clearing dirty
   * state. This is the local "discard unsaved edits" action, distinct
   * from the server-side {@link #resetToDefaultsServer}.
   */
  function reset(): void {
    all.value = { ...snapshot.value }
  }

  // ===== server-side destructive actions (still owned by this store) =====

  async function resetToDefaultsServer(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const data = await settingsApi.resetToDefaults()
      all.value = { ...data }
      snapshot.value = { ...data }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to reset system settings')
      console.error('Failed to reset system settings:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function clearCache(): Promise<{ clearedScopes: string[]; timestamp: string }> {
    clearingCache.value = true
    error.value = null
    try {
      return await settingsApi.clearCache()
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to clear system settings cache')
      console.error('Failed to clear system settings cache:', err)
      throw err
    } finally {
      clearingCache.value = false
    }
  }

  function clearError(): void {
    error.value = null
  }

  return {
    // state
    all,
    loading,
    saving,
    clearingCache,
    error,
    // per-category read slices
    general,
    email,
    rateLimits,
    uploads,
    features,
    // dirty flags
    isDirty,
    isGeneralDirty,
    isEmailDirty,
    isRateLimitsDirty,
    isUploadsDirty,
    isFeaturesDirty,
    // per-category patch (component edits)
    patchGeneral,
    patchEmail,
    patchRateLimits,
    patchUploads,
    patchFeatures,
    // lifecycle
    load,
    reset,
    clearError,
    // per-category save
    saveGeneral,
    saveEmail,
    saveRateLimits,
    saveUploads,
    saveFeatures,
    saveAllDirty,
    // server actions
    resetToDefaultsServer,
    clearCache,
  }
})
