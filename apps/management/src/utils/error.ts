import { ApiError } from './request'

export interface ErrorContext {
  title: string
  message: string
  suggestion?: string
  canRetry: boolean
}

/**
 * Extract a user-facing message from a thrown error.
 *
 * Normalizes the three inline patterns that had been copy-pasted across the
 * admin Pinia stores:
 *   1. `(err as { response?: { data?: { message?: string } } })?.response?.data?.message`
 *   2. `const err = e as { response?: ... }; err.response?.data?.message`
 *   3. The cast-then-fallback in `getErrorContext` below.
 *
 * All API rejections produced by `@/utils/request` are `ApiError` instances
 * (the http-client seam converts every AxiosError via `ApiError.fromAxiosError`,
 * which already extracts `response.data.message` into the `message` field).
 * The defensive casts remain to handle non-API errors thrown by callers
 * (e.g. Zod validation, network failures before the interceptor sees them).
 *
 * Architecture review Card #9 — see /tmp/architecture-review-1783403689.html.
 */
export function extractApiErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    return err.message || fallback
  }
  // Defensive path for non-ApiError throws that still carry an Axios-shaped
  // response (e.g. errors that escaped the http-client interceptor).
  const responseMessage = (err as { response?: { data?: { message?: string } } })?.response?.data
    ?.message
  if (responseMessage) return responseMessage
  if (err instanceof Error && err.message) return err.message
  return fallback
}

export function getErrorContext(
  error: unknown,
  action: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): ErrorContext {
  const apiError = error instanceof ApiError ? error : null
  const statusCode = apiError?.code || 0
  const errorMessage =
    apiError?.response?.data?.message ||
    (error instanceof Error ? error.message : null) ||
    'Unknown error'

  switch (statusCode) {
    case 400:
      return {
        title: t('errors.errorType.validation.title'),
        message: errorMessage || t('errors.errorType.validation.default'),
        suggestion: t('errors.errorType.validation.suggestion'),
        canRetry: false,
      }
    case 401:
      return {
        title: t('errors.errorType.unauthorized.title'),
        message: t('errors.errorType.unauthorized.message'),
        suggestion: t('errors.errorType.unauthorized.suggestion'),
        canRetry: false,
      }
    case 403:
      return {
        title: t('errors.errorType.forbidden.title'),
        message: t('errors.errorType.forbidden.message'),
        suggestion: t('errors.errorType.forbidden.suggestion'),
        canRetry: false,
      }
    case 404:
      return {
        title: t('errors.errorType.notFound.title'),
        message: `${action} ${t('errors.errorType.notFound.message')}`,
        suggestion: t('errors.errorType.notFound.suggestion'),
        canRetry: false,
      }
    case 500:
    case 502:
    case 503:
      return {
        title: t('errors.errorType.serverError.title'),
        message: t('errors.errorType.serverError.message'),
        suggestion: t('errors.errorType.serverError.suggestion'),
        canRetry: true,
      }
    default:
      return {
        title: t('errors.errorType.network.title'),
        message: errorMessage,
        suggestion: t('errors.errorType.network.suggestion'),
        canRetry: true,
      }
  }
}
