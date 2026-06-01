import { ApiError } from './request'

export interface ErrorContext {
  title: string
  message: string
  suggestion?: string
  canRetry: boolean
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
