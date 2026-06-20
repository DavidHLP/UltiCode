import axios, {
  type InternalAxiosRequestConfig,
  type AxiosResponse,
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosInstance,
} from 'axios'
import { LOCALE_HEADER_KEY, getActiveLocale, i18n } from '@/i18n'
import { csrfManager } from '@/utils/csrf'
import { createCsrfAxiosInterceptor, createRefreshAccessToken } from '@/shared/auth-core/src'
import router from '@/router'

/**
 * Standard API Response wrapper (matches backend Result<T>)
 * code: 0 = success, non-zero = error
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

/**
 * Extended request configuration with enterprise features
 */
export interface RequestConfig extends AxiosRequestConfig {
  retry?: number
  retryDelay?: number
  skipErrorHandler?: boolean
  skipResponseUnwrap?: boolean
  requestId?: string
}

/**
 * Custom API Error class
 */
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

/**
 * Request metadata for tracking
 */
interface RequestMetadata {
  requestId: string
  startTime: number
  retryCount: number
}

/**
 * Extended config with metadata
 */
interface ConfigWithMetadata
  extends Omit<InternalAxiosRequestConfig, 'headers'>, Omit<RequestConfig, 'headers'> {
  headers: InternalAxiosRequestConfig['headers']
  _metadata?: RequestMetadata
}

/**
 * Pending requests map for deduplication
 */
const pendingRequests = new Map<string, AbortController>()

/**
 * URLs that should never be deduplicated
 * These are auth-critical requests that must always go through
 */
const NON_DEDUPLICABLE_URLS = new Set(['/auth/me', '/auth/login', '/auth/logout', '/auth/register'])

/**
 * Generate unique request ID
 */
function generateRequestId(): string {
  return `req_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
}

/**
 * Generate request key for deduplication
 */
function getRequestKey(config: InternalAxiosRequestConfig): string {
  const { method, url, params, data } = config
  return `${method}_${url}_${JSON.stringify(params)}_${JSON.stringify(data)}`
}

/**
 * Get environment specific settings
 */
const isDevelopment = import.meta.env.DEV
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9001'

/**
 * Create axios instance with default config
 */
const service: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true, // Important: include cookies in all requests
  headers: {
    'Content-Type': 'application/json',
  },
})

const refreshAccessToken = createRefreshAccessToken(csrfManager)
const csrfInterceptors = createCsrfAxiosInterceptor(csrfManager, API_BASE_URL, refreshAccessToken)
service.interceptors.request.use(csrfInterceptors.requestInterceptor)

service.interceptors.response.use(
  csrfInterceptors.responseInterceptor,
  csrfInterceptors.errorInterceptor,
)

/**
 * Request interceptor with enterprise features
 */
service.interceptors.request.use(
  (config: ConfigWithMetadata) => {
    // Generate request ID for tracing
    const requestId = config.requestId || generateRequestId()
    config.headers['X-Request-ID'] = requestId
    config._metadata = {
      requestId,
      startTime: Date.now(),
      retryCount: 0,
    }

    // Note: Auth is now handled via httpOnly cookies (withCredentials: true)
    // No need to manually attach Authorization header

    // Add locale headers (both custom x-locale and standard Accept-Language)
    const activeLocale = getActiveLocale()
    config.headers[LOCALE_HEADER_KEY] = activeLocale
    config.headers['Accept-Language'] = activeLocale

    const isStateChangingMethod = ['patch', 'put', 'delete'].includes(
      config.method?.toLowerCase() || '',
    )
    const shouldDeduplicate = !NON_DEDUPLICABLE_URLS.has(config.url || '') && !isStateChangingMethod
    if (shouldDeduplicate) {
      const key = getRequestKey(config)
      if (pendingRequests.has(key)) {
        const controller = pendingRequests.get(key)!
        controller.abort()
        pendingRequests.delete(key)
      }

      const controller = new AbortController()
      config.signal = controller.signal
      pendingRequests.set(key, controller)
    }

    // Log request in development
    if (isDevelopment) {
      console.debug({
        method: config.method?.toUpperCase(),
        url: config.url,
        headers: config.headers,
        params: config.params,
        data: config.data,
      })
    }

    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  },
)

/**
 * Response interceptor with enterprise features
 */
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config as ConfigWithMetadata
    const metadata = config._metadata

    // Remove from pending requests (skip for non-deduplicated URLs)
    if (config && !NON_DEDUPLICABLE_URLS.has(config.url || '')) {
      const key = getRequestKey(config)
      pendingRequests.delete(key)
    }

    // Log response in development
    if (isDevelopment && metadata) {
      const duration = Date.now() - metadata.startTime
      console.debug({
        status: response.status,
        duration: `${duration}ms`,
        data: response.data,
      })
    }

    // Return full response for download requests, unwrapped data otherwise
    if (config.skipResponseUnwrap) {
      return response
    }

    // Backend response format: { code: 0, message: "success", data: {...}, traceId: "..." }
    // Unwrap to return just the data field
    const responseData = response.data
    if (responseData && typeof responseData === 'object' && 'code' in responseData) {
      if (responseData.code === 0) {
        // Success response - return data field if present, otherwise null
        const rawData = 'data' in responseData ? responseData.data : null
        return rawData
      }
      // Error response - throw with message
      const errorMessage = responseData.message || 'Request failed'
      throw new ApiError(errorMessage, responseData.code || 0, response)
    }

    // Fallback for non-standard responses - return data directly
    return response.data as never
  },
  async (error: AxiosError) => {
    const config = error.config as ConfigWithMetadata | undefined

    // Remove from pending requests (skip for non-deduplicated URLs)
    if (config && !NON_DEDUPLICABLE_URLS.has(config.url || '')) {
      const key = getRequestKey(config)
      pendingRequests.delete(key)
    }

    // Handle request cancellation
    if (error.name === 'CanceledError' || error.code === 'ERR_CANCELED') {
      if (isDevelopment) {
        console.debug('Request canceled')
      }
      // Use -1 instead of 0 to avoid conflict with backend's success code
      return Promise.reject(new ApiError(i18n.global.t('errors.apiErrorCanceled'), -1))
    }

    // Retry logic for network errors and 5xx
    if (config) {
      const enableRetry = config.retry === undefined ? true : config.retry > 0
      const metadata = config._metadata || {
        requestId: 'unknown',
        startTime: Date.now(),
        retryCount: 0,
      }
      const retryCount = metadata.retryCount || 0
      const maxRetry = config.retry || 2

      if (
        enableRetry &&
        retryCount < maxRetry &&
        (!error.response || error.response.status >= 500)
      ) {
        metadata.retryCount = retryCount + 1
        config._metadata = metadata

        const delay = config.retryDelay || 1000 * (retryCount + 1)
        await new Promise((resolve) => setTimeout(resolve, delay))

        if (isDevelopment) {
          console.debug({
            attempt: retryCount + 1,
            maxRetry,
            delay,
          })
        }

        return service(config)
      }
    }

    // Handle authentication errors
    // Cookies are httpOnly - cannot be removed by JavaScript
    // Backend will handle cookie clearing on logout
    if (error.response) {
      if (error.response.status === 401) {
        // Clear authentication state to prevent stale session data
        const { useAuthStore } = await import('@/stores/auth')
        const authStore = useAuthStore()
        if (authStore.isAuthenticated) {
          authStore.clearUser()
        }
        // Unauthorized - redirect to login page
        if (router.currentRoute.value.name !== 'login') {
          router.push('/login')
        }
        return Promise.reject(ApiError.fromAxiosError(error))
      }
      // 403 Forbidden - user is authenticated but lacks permission
      // Don't redirect, let the component handle the error gracefully
      if (error.response.status === 403) {
        if (isDevelopment && config?._metadata) {
          console.warn(`[API Forbidden] ${config._metadata.requestId}`, {
            url: config.url,
            message: 'Permission denied',
          })
        }
        return Promise.reject(ApiError.fromAxiosError(error))
      }
    }

    // Log other errors - skip auth errors
    if (isDevelopment && config?._metadata) {
      const status = error.response?.status
      // Skip logging 401/403 - already handled above with redirect
      if (status !== 401 && status !== 403) {
        console.error(`[API Error] ${config._metadata.requestId}`, {
          status: error.response?.status,
          message: error.message,
          data: error.response?.data,
        })
      }
    } else if (isDevelopment) {
      console.error('Request error:', error)
    }

    return Promise.reject(ApiError.fromAxiosError(error))
  },
)

/**
 * HTTP Methods with full type safety
 */

export async function apiGet<T = unknown>(
  path: string,
  init?: RequestConfig & { signal?: AbortSignal },
): Promise<T> {
  const { signal, ...axiosConfig } = (init || {}) as RequestConfig & { signal?: AbortSignal }
  return service.get<T, T>(path, {
    ...axiosConfig,
    signal,
  })
}

export async function apiPost<T = unknown>(
  path: string,
  body?: unknown,
  init?: RequestConfig,
): Promise<T> {
  return service.post<T, T, unknown>(path, body, { ...init })
}

export async function apiPut<T = unknown>(
  path: string,
  body?: unknown,
  init?: RequestConfig,
): Promise<T> {
  return service.put<T, T, unknown>(path, body, { ...init })
}

export async function apiPatch<T = unknown>(
  path: string,
  body?: unknown,
  init?: RequestConfig,
): Promise<T> {
  return service.patch<T, T, unknown>(path, body, { ...init })
}

export async function apiDelete<T = unknown>(path: string, init?: RequestConfig): Promise<T> {
  return service.delete<T, T>(path, { ...init })
}

/**
 * Upload file with progress tracking
 */
export async function apiUpload<T = unknown>(
  path: string,
  file: File | Blob,
  onProgress?: (progress: number) => void,
  init?: RequestConfig,
): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)

  return service.post<T, T>(path, formData, {
    ...init,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(progress)
      }
    },
  })
}

/**
 * Download file
 */
export async function apiDownload(
  path: string,
  filename?: string,
  init?: RequestConfig,
): Promise<void> {
  // Use axiosInstance directly to get raw response for binary data
  const response = await (service as AxiosInstance).get<Blob>(path, {
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
}

/**
 * Create abort controller for manual cancellation
 */
export function createAbortController(): AbortController {
  return new AbortController()
}

export { service as axiosInstance }
export default service
