import axios, {
  type InternalAxiosRequestConfig,
  type AxiosResponse,
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosInstance,
  type CancelTokenSource,
} from 'axios'

/**
 * Standard API Response wrapper
 */
export interface ApiResponse<T = unknown> {
  success: boolean
  data: T
  message?: string
  code?: number
}

/**
 * Extended request configuration with enterprise features
 */
export interface RequestConfig extends AxiosRequestConfig {
  retry?: number
  retryDelay?: number
  skipAuth?: boolean
  skipErrorHandler?: boolean
  cache?: boolean
  cacheTTL?: number
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
    const message =
      (error.response?.data as { message?: string })?.message ||
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
interface ConfigWithMetadata extends Omit<InternalAxiosRequestConfig, 'headers'>, Omit<RequestConfig, 'headers'> {
  headers: any
  _metadata?: RequestMetadata
}

/**
 * Pending requests map for deduplication
 */
const pendingRequests = new Map<string, CancelTokenSource>()

/**
 * Generate unique request ID
 */
function generateRequestId(): string {
  return `admin_req_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
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
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000'

/**
 * Create axios instance with default config
 */
const service: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

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

    // Add auth token if available
    if (!config.skipAuth) {
      const token = localStorage.getItem('admin_token')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }

    // Request deduplication
    const key = getRequestKey(config)
    if (pendingRequests.has(key)) {
      const source = pendingRequests.get(key)!
      config.cancelToken = source.token
      return config
    }

    const source = axios.CancelToken.source()
    config.cancelToken = source.token
    pendingRequests.set(key, source)

    // Log request in development
    if (isDevelopment) {
      console.log(`[Admin API Request] ${requestId}`, {
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

    // Remove from pending requests
    if (config) {
      const key = getRequestKey(config)
      pendingRequests.delete(key)
    }

    // Log response in development
    if (isDevelopment && metadata) {
      const duration = Date.now() - metadata.startTime
      console.log(`[Admin API Response] ${metadata.requestId}`, {
        status: response.status,
        duration: `${duration}ms`,
        data: response.data,
      })
    }

    // Return unwrapped data by default
    return response.data
  },
  async (error: AxiosError) => {
    const config = error.config as ConfigWithMetadata | undefined

    // Remove from pending requests
    if (config) {
      const key = getRequestKey(config)
      pendingRequests.delete(key)
    }

    // Handle request cancellation
    if (axios.isCancel(error)) {
      console.log('Admin request canceled:', error.message)
      return Promise.reject(new ApiError('Request canceled', 0))
    }

    // Retry logic for network errors and 5xx
    if (config) {
      const enableRetry = config.retry === undefined ? true : config.retry > 0
      const metadata = config._metadata || { requestId: 'unknown', startTime: Date.now(), retryCount: 0 }
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
          console.log(`[Admin API Retry] ${metadata.requestId}`, {
            attempt: retryCount + 1,
            maxRetry,
            delay,
          })
        }

        return service(config)
      }
    }

    // Handle authentication errors - redirect to login
    if (error.response?.status === 401) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    // Log error
    if (isDevelopment && config?._metadata) {
      console.error(`[Admin API Error] ${config._metadata.requestId}`, {
        status: error.response?.status,
        message: error.message,
        data: error.response?.data,
      })
    } else {
      console.error('Admin request error:', error)
    }

    return Promise.reject(ApiError.fromAxiosError(error))
  },
)

/**
 * HTTP Methods with full type safety
 */

export async function apiGet<T = unknown>(
  path: string,
  init?: RequestConfig,
): Promise<T> {
  return service.get<T, T>(path, { ...init })
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

export async function apiDelete<T = unknown>(
  path: string,
  init?: RequestConfig,
): Promise<T> {
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
  })

  const url = window.URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename || 'download')
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

/**
 * Create cancel token source for manual cancellation
 */
export function createCancelToken(): CancelTokenSource {
  return axios.CancelToken.source()
}

export { service as axiosInstance }
export default service
