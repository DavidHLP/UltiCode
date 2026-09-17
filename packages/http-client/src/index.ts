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

/** Request options shared by all HTTP helpers without exposing the transport implementation. */
export interface RequestConfig {
  params?: unknown
  data?: unknown
  headers?: Record<string, string | number | boolean | null | undefined>
  signal?: AbortSignal
  timeout?: number
  responseType?: 'arraybuffer' | 'blob' | 'document' | 'json' | 'text'
  withCredentials?: boolean
  retry?: number
  retryDelay?: number
  skipErrorHandler?: boolean
  skipResponseUnwrap?: boolean
  requestId?: string
}

type ApiErrorData = {
  message?: string
  [key: string]: unknown
}

/** Narrow response payloads to the object shape used for API error messages. */
function isApiErrorData(value: unknown): value is ApiErrorData {
  return typeof value === 'object' && value !== null
}

/** Transport-neutral response details retained on an {@link ApiError}. */
export interface ApiErrorResponse {
  status: number
  statusText?: string
  data?: ApiErrorData | null
}

/** Convert transport response data to the package-owned error response shape. */
function toApiErrorResponse(response?: AxiosResponse): ApiErrorResponse | undefined {
  if (!response) return undefined
  const data = response.data
  return {
    status: response.status,
    statusText: response.statusText,
    data:
      data === null
        ? null
        : isApiErrorData(data)
          ? data
          : typeof data === 'string'
            ? { message: data }
            : undefined,
  }
}

/** Custom API Error class. */
export class ApiError extends Error {
  public code: number
  public response?: ApiErrorResponse

  constructor(message: string, code: number, response?: ApiErrorResponse) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.response = response
    Object.setPrototypeOf(this, ApiError.prototype)
  }

  static fromAxiosError(error: unknown): ApiError {
    const axiosError = axios.isAxiosError(error) ? error : undefined
    const data = axiosError?.response?.data
    const message =
      (isApiErrorData(data) && typeof data.message === 'string' ? data.message : undefined) ||
      (error instanceof Error ? error.message : undefined) ||
      'Request failed'
    const code = axiosError?.response?.status || 0
    return new ApiError(message, code, toApiErrorResponse(axiosError?.response))
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
 * - 'non-auth-readonly': dedup only non-auth GET requests (management
 *   default); never dedup state-changing methods.
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
  __testAdapter?: unknown
}

// ---------------------------------------------------------------------------
// Per-request metadata + dedup machinery.
// ---------------------------------------------------------------------------

interface RequestMetadata {
  requestId: string
  startTime: number
  retryCount: number
  callerSignal?: AbortSignal
  cleanupCallerSignal?: () => void
}

interface ConfigWithMetadata extends Omit<InternalAxiosRequestConfig, 'headers'> {
  headers: AxiosRequestHeaders
  retry?: number
  retryDelay?: number
  skipErrorHandler?: boolean
  skipResponseUnwrap?: boolean
  requestId?: string
  _metadata?: RequestMetadata
}

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
  return method === 'get'
}

// ---------------------------------------------------------------------------
// `createHttpClient` — the seam.
// ---------------------------------------------------------------------------

/** Public HTTP method bundle returned by {@link createHttpClient}. */
export interface HttpClient {
  /** `GET /path` returning the unwrapped `data` field of the `Result<T>` envelope. */
  apiGet: <T = unknown>(path: string, init?: RequestConfig) => Promise<T>
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
  const pendingRequests = new Map<string, AbortController>()
  const clearPendingRequest = (request: InternalAxiosRequestConfig): void => {
    const key = getRequestKey(request)
    const pendingController = pendingRequests.get(key)
    if (pendingController?.signal === request.signal) {
      pendingRequests.delete(key)
    }
    const metadata = (request as ConfigWithMetadata)._metadata
    metadata?.cleanupCallerSignal?.()
    if (metadata) metadata.cleanupCallerSignal = undefined
  }
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
    service.defaults.adapter = config.__testAdapter as AxiosAdapter
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
      const previousMetadata = req._metadata
      previousMetadata?.cleanupCallerSignal?.()
      const metadata: RequestMetadata = previousMetadata ?? {
        requestId: req.requestId || generateRequestId(),
        startTime: Date.now(),
        retryCount: 0,
        callerSignal: req.signal as AbortSignal | undefined,
      }
      const callerSignal = metadata.callerSignal
      const isRetry = metadata.retryCount > 0
      req.headers['X-Request-ID'] = metadata.requestId
      req._metadata = metadata
      const activeLocale = config.getLocale()
      req.headers[LOCALE_HEADER_KEY] = activeLocale
      req.headers['Accept-Language'] = activeLocale

      if (shouldDeduplicate(req, dedupPolicy)) {
        const key = getRequestKey(req)
        const controller = new AbortController()
        if (callerSignal) {
          const onCallerAbort = (): void => controller.abort()
          if (callerSignal.aborted) {
            controller.abort()
          } else {
            callerSignal.addEventListener('abort', onCallerAbort, { once: true })
            if (callerSignal.aborted) controller.abort()
            metadata.cleanupCallerSignal = () =>
              callerSignal.removeEventListener('abort', onCallerAbort)
          }
        }
        req.signal = controller.signal
        if (!isRetry) {
          pendingRequests.get(key)?.abort()
          pendingRequests.set(key, controller)
        }
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
        clearPendingRequest(cfg)
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
            new ApiError(
              apiResponse.message || 'Request failed',
              apiResponse.code,
              toApiErrorResponse(response),
            ),
          )
        }
        return apiResponse.data
      }
      return response.data
    },
    async (error: AxiosError) => {
      const cfg = error.config as ConfigWithMetadata | undefined
      if (cfg && !NON_DEDUPLICABLE_URLS.has(cfg.url || '')) {
        clearPendingRequest(cfg)
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

  // axios types the response as `AxiosResponseResult<T, R, D, P>`, a conditional
  // type that cannot collapse when the caller passes its own response type
  // parameter. The response interceptor already unwraps the envelope, so the
  // runtime value is the payload; assert that once here, not at every call site.
  const asPayload = <T>(request: Promise<unknown>): Promise<T> => request as Promise<T>
  const toAxiosConfig = (init?: RequestConfig): AxiosRequestConfig => {
    if (!init) return {}
    const {
      params,
      data,
      headers,
      signal,
      timeout,
      responseType,
      withCredentials,
      retry,
      retryDelay,
      skipErrorHandler,
      skipResponseUnwrap,
      requestId,
    } = init
    return {
      params,
      data,
      headers,
      signal,
      timeout,
      responseType,
      withCredentials,
      retry,
      retryDelay,
      skipErrorHandler,
      skipResponseUnwrap,
      requestId,
    } as AxiosRequestConfig
  }

  return {
    apiGet: <T>(path: string, init?: RequestConfig) =>
      asPayload<T>(service.get<T, T>(path, toAxiosConfig(init))),
    apiPost: <T>(path: string, body?: unknown, init?: RequestConfig) =>
      asPayload<T>(service.post<T, T, unknown>(path, body, toAxiosConfig(init))),
    apiPatch: <T>(path: string, body?: unknown, init?: RequestConfig) =>
      asPayload<T>(service.patch<T, T, unknown>(path, body, toAxiosConfig(init))),
    apiPut: <T>(path: string, body?: unknown, init?: RequestConfig) =>
      asPayload<T>(service.put<T, T, unknown>(path, body, toAxiosConfig(init))),
    apiDelete: <T>(path: string, init?: RequestConfig) =>
      asPayload<T>(service.delete<T, T>(path, toAxiosConfig(init))),
    apiUpload: <T>(
      path: string,
      file: File | Blob,
      onProgress?: (progress: number) => void,
      init?: RequestConfig,
    ) => {
      const formData = new FormData()
      formData.append('file', file)
      return asPayload<T>(
        service.post<T, T>(path, formData, {
          ...toAxiosConfig(init),
          headers: {
            'Content-Type': 'multipart/form-data',
            ...init?.headers,
          },
          onUploadProgress: (progressEvent) => {
            if (onProgress && progressEvent.total) {
              const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
              onProgress(progress)
            }
          },
        }),
      )
    },
    apiDownload: async (path: string, filename?: string, init?: RequestConfig) => {
      const response = await service.get<Blob>(path, {
        ...toAxiosConfig(init),
        responseType: 'blob',
        skipResponseUnwrap: true,
      } as AxiosRequestConfig)
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