import { describe, it, expect } from 'vitest'
import { getErrorContext } from '@/utils/error'
import { ApiError } from '@/utils/request'

const mockT = (key: string) => {
  const translations: Record<string, string> = {
    'errors.validation.title': 'Validation Error',
    'errors.validation.default': 'Invalid input',
    'errors.validation.suggestion': 'Please check your input',
    'errors.unauthorized.title': 'Unauthorized',
    'errors.unauthorized.message': 'Please log in',
    'errors.unauthorized.suggestion': 'Go to login page',
    'errors.forbidden.title': 'Forbidden',
    'errors.forbidden.message': 'Access denied',
    'errors.forbidden.suggestion': 'Contact administrator',
    'errors.notFound.title': 'Not Found',
    'errors.notFound.message': 'not found',
    'errors.notFound.suggestion': 'Check the ID',
    'errors.serverError.title': 'Server Error',
    'errors.serverError.message': 'Something went wrong',
    'errors.serverError.suggestion': 'Try again later',
    'errors.network.title': 'Network Error',
    'errors.network.suggestion': 'Check your connection',
  }
  return translations[key] || key
}

describe('getErrorContext', () => {
  describe('when error is ApiError with status code', () => {
    it('should return validation context for 400 status', () => {
      const error = new ApiError('Invalid field', 400, {
        data: { message: 'Invalid field' },
      } as any)
      const result = getErrorContext(error, 'Create', mockT)

      expect(result.title).toBe('Validation Error')
      expect(result.message).toBe('Invalid field')
      expect(result.canRetry).toBe(false)
    })

    it('should return unauthorized context for 401 status', () => {
      const error = new ApiError('Unauthorized', 401)
      const result = getErrorContext(error, 'Access', mockT)

      expect(result.title).toBe('Unauthorized')
      expect(result.message).toBe('Please log in')
      expect(result.canRetry).toBe(false)
    })

    it('should return forbidden context for 403 status', () => {
      const error = new ApiError('Forbidden', 403)
      const result = getErrorContext(error, 'Delete', mockT)

      expect(result.title).toBe('Forbidden')
      expect(result.message).toBe('Access denied')
      expect(result.canRetry).toBe(false)
    })

    it('should return not found context for 404 status', () => {
      const error = new ApiError('Not Found', 404)
      const result = getErrorContext(error, 'Update problem', mockT)

      expect(result.title).toBe('Not Found')
      expect(result.message).toBe('Update problem not found')
      expect(result.canRetry).toBe(false)
    })

    it('should return server error context for 500 status', () => {
      const error = new ApiError('Internal Server Error', 500)
      const result = getErrorContext(error, 'Submit', mockT)

      expect(result.title).toBe('Server Error')
      expect(result.message).toBe('Something went wrong')
      expect(result.canRetry).toBe(true)
    })

    it('should return server error context for 502 status', () => {
      const error = new ApiError('Bad Gateway', 502)
      const result = getErrorContext(error, 'Fetch', mockT)

      expect(result.canRetry).toBe(true)
    })

    it('should return server error context for 503 status', () => {
      const error = new ApiError('Service Unavailable', 503)
      const result = getErrorContext(error, 'Call', mockT)

      expect(result.canRetry).toBe(true)
    })
  })

  describe('when error is not ApiError', () => {
    it('should return network error context for generic error', () => {
      const error = new Error('Network connection failed')
      const result = getErrorContext(error, 'Test', mockT)

      expect(result.title).toBe('Network Error')
      expect(result.message).toBe('Network connection failed')
      expect(result.canRetry).toBe(true)
    })

    it('should return "Unknown error" for null/undefined error', () => {
      const result = getErrorContext(null, 'Test', mockT)

      expect(result.message).toBe('Unknown error')
    })

    it('should handle error without message', () => {
      const error = {} as Error
      const result = getErrorContext(error, 'Test', mockT)

      expect(result.message).toBe('Unknown error')
    })
  })

  describe('action context', () => {
    it('should include action in not found message', () => {
      const error = new ApiError('Not Found', 404)
      const result = getErrorContext(error, 'Delete problem', mockT)

      expect(result.message).toBe('Delete problem not found')
    })
  })
})
