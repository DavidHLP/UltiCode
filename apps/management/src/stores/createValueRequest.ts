import { createValueRequest as createSharedValueRequest } from '@ulticode/request-state'
import { extractApiErrorMessage } from '@/utils/error'

interface CreateValueRequestOptions {
  errorMessage: string
  rethrow?: boolean
  onError?: (err: unknown) => void
}

/** Newest-wins request state and cancellation policy for single-value reads. */
export function createValueRequest(options: CreateValueRequestOptions) {
  return createSharedValueRequest({
    ...options,
    getErrorMessage: extractApiErrorMessage,
  })
}
