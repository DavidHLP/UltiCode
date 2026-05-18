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
        title: t('errors.validation.title'),
        message: errorMessage || t('errors.validation.default'),
        suggestion: t('errors.validation.suggestion'),
        canRetry: false,
      }
    case 401:
      return {
        title: t('errors.unauthorized.title'),
        message: t('errors.unauthorized.message'),
        suggestion: t('errors.unauthorized.suggestion'),
        canRetry: false,
      }
    case 403:
      return {
        title: t('errors.forbidden.title'),
        message: t('errors.forbidden.message'),
        suggestion: t('errors.forbidden.suggestion'),
        canRetry: false,
      }
    case 404:
      return {
        title: t('errors.notFound.title'),
        message: `${action} ${t('errors.notFound.message')}`,
        suggestion: t('errors.notFound.suggestion'),
        canRetry: false,
      }
    case 500:
    case 502:
    case 503:
      return {
        title: t('errors.serverError.title'),
        message: t('errors.serverError.message'),
        suggestion: t('errors.serverError.suggestion'),
        canRetry: true,
      }
    default:
      return {
        title: t('errors.network.title'),
        message: errorMessage,
        suggestion: t('errors.network.suggestion'),
        canRetry: true,
      }
  }
}
