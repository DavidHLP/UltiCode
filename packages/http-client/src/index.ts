/**
 * Shared HTTP client deep module.
 *
 * <p>Replaces the byte-for-byte duplicate request.ts implementations that
 * existed in `console/src/utils/request.ts` (411 LoC) and
 * `management/src/utils/request.ts` (440 LoC). Both files were ~95%
 * copy-paste; the only real divergences were:
 * <ul>
 *   <li>401 destination (console: `clearUser()` + optional session-expired
 *       callback; management: `clearUser()` + `router.push('/login')`)</li>
 *   <li>Dedup policy (console: skip auth URLs only; management: also skip
 *       state-changing methods PATCH/PUT/DELETE)</li>
 *   <li>Console only exposes apiGet/apiPost/apiPatch/apiPut/apiDelete;
 *       management additionally ships apiUpload and apiDownload.</li>
 * </ul>
 *
 * <p>{@link createHttpClient} is the seam. Both apps pass a {@link HttpClientConfig}
 * that captures their divergence and consume the same returned
 * `apiGet / apiPost / apiPatch / apiPut / apiDelete / apiUpload / apiDownload`
 * helpers. A change to retry policy, CSRF wiring, error envelope, or
 * response unwrap now lands once.
 *
 * <p>See `/tmp/architecture-review-1783341079.html` Card 2.
 */
import axios, {
  type AxiosAdapter,
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosRequestHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import {
  type CsrfTokenManager,
} from '@ulticode/auth-core/src/csrf'
import {
  createCsrfAxiosInterceptor,
} from '@ulticode/auth-core/src/axiosCsrfInterceptor'
import {
  createRefreshAccessToken,
} from '@ulticode/auth-core/src/refreshCoordinator'

// ---------------------------------------------------------------------------
// Locale header — the host app injects the active locale via a callback so
// the http-client package does not import from `@/i18n` (a path that
// doesn't exist in this workspace package).
// ---------------------------------------------------------------------------

/** Caller-supplied resolver for the active locale, used as the `x-locale` and `Accept-Language` header value. */
export type LocaleResolver = () => string

/** Header key used to forward the active locale. Host apps should import the same constant from their i18n module. */
export const LOCALE_HEADER_KEY = 'x-locale'

// ---------------------------------------------------------------------------
// Result envelope, request config, error class — unchanged from the prior
// byte-identical console/management definitions.
// ---------------------------------------------------------------------------

/** Standard API Response wrapper (matches backend `Result<T>`). `code: 0` is success. */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** Extended request configuration with enterprise features. */
export interface RequestConfig extends AxiosRequestConfig {
  retry?: number
  retryDelay?: number
  skipErrorHandler?: boolean
  skipResponseUnwrap?: boolean
  requestId?: string
}

/** Custom API Error class. */
export class ApiError extends Error {
  public code: number
  public response?: AxiosResponse

  constructor(message: string, code: number, response?: AxiosResponse) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.response = response
    Object.setPrototypeOf(this, ApiError.prototype)
  }

  static fromAxiosError(error: AxiosError): ApiError {
    const data = error.response?.data
    const message =
      (typeof data === 'object' && data !== null && 'message' in data
        ? (data as { message?: string }).message
        : undefined) ||
      error.message ||
      'Request failed'
    const code = error.response?.status || 0
    return new ApiError(message, code, error.response)
  }
}

// ---------------------------------------------------------------------------
// Config types for `createHttpClient` — typed config is the seam.
// ---------------------------------------------------------------------------

/**
 * What should happen when an API call returns 401/403.
 *
 * - `'clear-and-run'`: invoke the provided callback (console style — useful
 *   for both a Pinia `clearUser()` and an optional session-expired listener).
 *   Default dedup guard prevents concurrent redirects on parallel 401s.
 * - `'redirect-login'`: clear user state then navigate to `/login` if the
 *   current route is not already login (management style).
 * - `'silent'`: do nothing — surface the error to the caller.
 */
export type AuthFailureStrategy =
  | { kind: 'clear-and-run'; onAuthFailure: () => void | Promise<void> }
  | { kind: 'redirect-login'; onAuthFailure: (path: string) => void }
  | { kind: 'silent' }

/**
 * Whether to deduplicate in-flight identical requests.
 *
 * - `'all-non-auth'`: dedup every non-auth URL (console default).
 * - `'non-auth-readonly'`: additionally skip state-changing methods
 *   PATCH/PUT/DELETE (management default — prevents accidental abort of
 *   in-flight write operations).
 * - `'none'`: never dedup.
 */
export type DedupPolicy = 'all-non-auth' | 'non-auth-readonly' | 'none'

/** URLs that should never be deduplicated (auth-critical). */
const NON_DEDUPLICABLE_URLS = new Set([
  '/auth/me',
  '/auth/login',
  '/auth/logout',
  '/auth/register',
])

/** Configuration for {@link createHttpClient}. */
export interface HttpClientConfig {
  /** CSRF token manager — typically a singleton from auth-core's `createCsrfTokenManager()`. */
  csrfManager: CsrfTokenManager
  /** Backend API base URL. Defaults to `import.meta.env.VITE_API_BASE_URL` if omitted. */
  baseURL?: string
  /** Resolve the active locale for `x-locale` / `Accept-Language` headers. */
  getLocale: LocaleResolver
  /** What happens on 401/403. Default: `'silent'`. */
  onAuthFailure?: AuthFailureStrategy
  /** Dedup policy. Default: `'non-auth-readonly'`. */
  dedupPolicy?: DedupPolicy
  /** Translation key / message used when a request is canceled. Default: `'Request canceled'`. */
  canceledMessage?: string
  /**
   * Test-only axios adapter injection — wires a mock adapter into the
   * underlying axios instance before any interceptors fire, so tests can
   * exercise the wrapper (dedup, retry, CSRF, 401 handling) without
   * network or MSW. Replaces the previous `client.axiosInstance.defaults.adapter`
   * escape hatch that exposed the raw axios instance through the public
   * interface. Production code MUST NOT set this.
   */
  __testAdapter?: AxiosAdapter
}

// ---------------------------------------------------------------------------
// Per-request metadata + dedup machinery.
// ---------------------------------------------------------------------------

interface RequestMetadata {
  requestId: string
  startTime: number
  retryCount: number
}

interface ConfigWithMetadata
  extends Omit<InternalAxiosRequestConfig, 'headers'>,
    Omit<RequestConfig, 'headers'> {
  headers: AxiosRequestHeaders
  _metadata?: RequestMetadata
}

const pendingRequests = new Map<string, AbortController>()

function generateRequestId(): string {
  return `req_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
}

function getRequestKey(config: InternalAxiosRequestConfig): string {
  const { method, url, params, data } = config
  return `${method}_${url}_${JSON.stringify(params)}_${JSON.stringify(data)}`
}

function shouldDeduplicate(config: InternalAxiosRequestConfig, policy: DedupPolicy): boolean {
  if (policy === 'none') return false
  if (NON_DEDUPLICABLE_URLS.has(config.url || '')) return false
  if (policy === 'all-non-auth') return true
  // 'non-auth-readonly'
  const method = config.method?.toLowerCase() || ''
  return !['patch', 'put', 'delete'].includes(method)
}

// ---------------------------------------------------------------------------
// `createHttpClient` — the seam.
// ---------------------------------------------------------------------------

/** Public HTTP method bundle returned by {@link createHttpClient}. */
export interface HttpClient {
  /** `GET /path` returning the unwrapped `data` field of the `Result<T>` envelope. */
  apiGet: <T = unknown>(path: string, init?: RequestConfig & { signal?: AbortSignal }) => Promise<T>
  /** `POST /path`. */
  apiPost: <T = unknown>(path: string, body?: unknown, init?: RequestConfig) => Promise<T>
  /** `PATCH /path`. */
  apiPatch: <T = unknown>(path: string, body?: unknown, init?: RequestConfig) => Promise<T>
  /** `PUT /path`. */
  apiPut: <T = unknown>(path: string, body?: unknown, init?: RequestConfig) => Promise<T>
  /** `DELETE /path`. */
  apiDelete: <T = unknown>(path: string, init?: RequestConfig) => Promise<T>
  /** Multipart upload with progress tracking. */
  apiUpload: <T = unknown>(
    path: string,
    file: File | Blob,
    onProgress?: (progress: number) => void,
    init?: RequestConfig,
  ) => Promise<T>
  /** Browser-side file download via a hidden `<a download>` element. */
  apiDownload: (path: string, filename?: string, init?: RequestConfig) => Promise<void>
  /** Build an `AbortController` consumers can use to cancel in-flight requests. */
  createAbortController: () => AbortController
}

/**
 * Build an HTTP client wired to a CSRF manager + locale resolver + auth
 * failure strategy + dedup policy. The returned object owns the underlying
 * Axios instance — multiple `createHttpClient` calls produce independent
 * instances (useful for testing).
 */
export function createHttpClient(config: HttpClientConfig): HttpClient {
  const dedupPolicy = config.dedupPolicy ?? 'non-auth-readonly'
  const canceledMessage = config.canceledMessage ?? 'Request canceled'
  let isAuthErrorHandling = false
  type ViteImportMeta = ImportMeta & {
    env?: {
      DEV?: boolean
      VITE_API_BASE_URL?: string
    }
  }
  const viteMeta = import.meta as ViteImportMeta
  const isDevelopment = Boolean(viteMeta.env?.DEV)
  const baseURL = config.baseURL ?? viteMeta.env?.VITE_API_BASE_URL ?? '/api'

  const service: AxiosInstance = axios.create({
    baseURL,
    timeout: 30000,
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' },
  })

  if (config.__testAdapter) {
    service.defaults.adapter = config.__testAdapter
  }

  const refreshAccessToken = createRefreshAccessToken(config.csrfManager)
  const csrfInterceptors = createCsrfAxiosInterceptor(
    config.csrfManager,
    baseURL,
    refreshAccessToken,
  )
  service.interceptors.request.use(csrfInterceptors.requestInterceptor)
  service.interceptors.response.use(
    undefined,
    csrfInterceptors.errorInterceptor,
  )

  service.interceptors.request.use(
    (req: ConfigWithMetadata) => {
      const requestId = req.requestId || generateRequestId()
      req.headers['X-Request-ID'] = requestId
      req._metadata = { requestId, startTime: Date.now(), retryCount: 0 }
      const activeLocale = config.getLocale()
      req.headers[LOCALE_HEADER_KEY] = activeLocale
      req.headers['Accept-Language'] = activeLocale

      if (shouldDeduplicate(req, dedupPolicy)) {
        const key = getRequestKey(req)
        if (pendingRequests.has(key)) {
          const controller = pendingRequests.get(key)!
          controller.abort()
          pendingRequests.delete(key)
        }
        const controller = new AbortController()
        req.signal = controller.signal
        pendingRequests.set(key, controller)
      }

      if (isDevelopment) {
        // eslint-disable-next-line no-console
        console.debug('[API Request]', {
          baseURL: req.baseURL,
          method: req.method?.toUpperCase(),
          url: req.url,
          headers: req.headers,
          params: req.params,
          data: req.data,
        })
      }
      return req
    },
    (error: AxiosError) => Promise.reject(error),
  )

  service.interceptors.response.use(
    (response: AxiosResponse) => {
      const cfg = response.config as ConfigWithMetadata
      const metadata = cfg._metadata
      if (cfg && !NON_DEDUPLICABLE_URLS.has(cfg.url || '')) {
        pendingRequests.delete(getRequestKey(cfg))
      }
      if (isDevelopment && metadata) {
        // eslint-disable-next-line no-console
        console.debug('[API Response]', {
          status: response.status,
          duration: `${Date.now() - metadata.startTime}ms`,
          data: response.data,
        })
      }
      if (cfg.skipResponseUnwrap) return response
      const data = response.data
      if (data && typeof data === 'object' && 'code' in data) {
        const apiResponse = data as ApiResponse<unknown>
        if (apiResponse.code !== 0) {
          return Promise.reject(
            new ApiError(apiResponse.message || 'Request failed', apiResponse.code, response),
          )
        }
        return apiResponse.data
      }
      return response.data
    },
    async (error: AxiosError) => {
      const cfg = error.config as ConfigWithMetadata | undefined
      if (cfg && !NON_DEDUPLICABLE_URLS.has(cfg.url || '')) {
        pendingRequests.delete(getRequestKey(cfg))
      }
      if (error.name === 'CanceledError' || error.code === 'ERR_CANCELED') {
        if (isDevelopment) {
          // eslint-disable-next-line no-console
          console.debug('[API] canceled', cfg?.url)
        }
        return Promise.reject(new ApiError(canceledMessage, -1))
      }
      if (cfg) {
        if (cfg.skipErrorHandler) {
          return Promise.reject(ApiError.fromAxiosError(error))
        }
        const enableRetry = cfg.retry === undefined ? true : cfg.retry > 0
        const metadata = cfg._metadata || {
          requestId: 'unknown',
          startTime: Date.now(),
          retryCount: 0,
        }
        const retryCount = metadata.retryCount || 0
        const maxRetry = cfg.retry || 2
        if (
          enableRetry &&
          retryCount < maxRetry &&
          (!error.response || error.response.status >= 500)
        ) {
          metadata.retryCount = retryCount + 1
          cfg._metadata = metadata
          const delay = cfg.retryDelay || 1000 * (retryCount + 1)
          await new Promise((resolve) => setTimeout(resolve, delay))
          if (isDevelopment) {
            // eslint-disable-next-line no-console
            console.debug('[API Retry]', { attempt: retryCount + 1, maxRetry, delay })
          }
          return service(cfg)
        }
      }
      const strategy = config.onAuthFailure ?? { kind: 'silent' as const }
      if (error.response && (error.response.status === 401 || error.response.status === 403)) {
        if (strategy.kind === 'clear-and-run') {
          if (!isAuthErrorHandling) {
            isAuthErrorHandling = true
            try {
              await strategy.onAuthFailure()
            } finally {
              setTimeout(() => {
                isAuthErrorHandling = false
              }, 1000)
            }
          }
        } else if (strategy.kind === 'redirect-login' && error.response.status === 401) {
          if (!isAuthErrorHandling) {
            isAuthErrorHandling = true
            try {
              strategy.onAuthFailure('/login')
            } finally {
              setTimeout(() => {
                isAuthErrorHandling = false
              }, 1000)
            }
          }
        } else if (strategy.kind === 'redirect-login' && error.response.status === 403) {
          if (isDevelopment && cfg?._metadata) {
            // eslint-disable-next-line no-console
            console.warn(`[API Forbidden] ${cfg._metadata.requestId}`, {
              url: cfg.url,
              message: 'Permission denied',
            })
          }
        }
        return Promise.reject(ApiError.fromAxiosError(error))
      }
      if (isDevelopment && cfg?._metadata) {
        const status = error.response?.status
        if (status !== 401 && status !== 403) {
          // eslint-disable-next-line no-console
          console.error(`[API Error] ${cfg._metadata.requestId}`, {
            status: error.response?.status,
            message: error.message,
            data: error.response?.data,
          })
        }
      } else if (!error.response && isDevelopment) {
        // eslint-disable-next-line no-console
        console.error('Request error:', error)
      }
      return Promise.reject(ApiError.fromAxiosError(error))
    },
  )

  return {
    apiGet: <T>(path: string, init?: RequestConfig & { signal?: AbortSignal }) => {
      const { signal, ...axiosConfig } = (init || {}) as RequestConfig & {
        signal?: AbortSignal
      }
      return service.get<T, T>(path, { ...axiosConfig, signal })
    },
    apiPost: <T>(path: string, body?: unknown, init?: RequestConfig) =>
      service.post<T, T, unknown>(path, body, { ...init }),
    apiPatch: <T>(path: string, body?: unknown, init?: RequestConfig) =>
      service.patch<T, T, unknown>(path, body, { ...init }),
    apiPut: <T>(path: string, body?: unknown, init?: RequestConfig) =>
      service.put<T, T, unknown>(path, body, { ...init }),
    apiDelete: <T>(path: string, init?: RequestConfig) =>
      service.delete<T, T>(path, { ...init }),
    apiUpload: <T>(
      path: string,
      file: File | Blob,
      onProgress?: (progress: number) => void,
      init?: RequestConfig,
    ) => {
      const formData = new FormData()
      formData.append('file', file)
      return service.post<T, T>(path, formData, {
        ...init,
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (progressEvent) => {
          if (onProgress && progressEvent.total) {
            const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            onProgress(progress)
          }
        },
      })
    },
    apiDownload: async (path: string, filename?: string, init?: RequestConfig) => {
      const response = await service.get<Blob>(path, {
        ...init,
        responseType: 'blob',
        skipResponseUnwrap: true,
      } as RequestConfig)
      const url = window.URL.createObjectURL(response.data as Blob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', filename || 'download')
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    },
    createAbortController: () => new AbortController(),
  }
}

// auth-core internals are no longer re-exported here; import them
// directly from '@ulticode/auth-core/src/{axiosCsrfInterceptor,refreshCoordinator}' if needed.