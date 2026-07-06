import { describe, expect, it, vi } from 'vitest'
import axios, { AxiosError } from 'axios'
import { createCsrfTokenManager } from '@ulticode/auth-core/src/csrf'
import { createHttpClient } from '../index'

/**
 * Build an AxiosError with the given HTTP status. Axios only invokes the
 * error interceptor when the adapter throws an error — a `resolve()` with a
 * 401 status counts as a successful HTTP response, so we need to fabricate
 * the error envelope ourselves to exercise the auth-failure and retry paths.
 */
function buildAxiosError(status: number, message: string): AxiosError {
  const err = new AxiosError(message)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ;(err as any).response = {
    status,
    statusText: message,
    headers: {},
    config: { headers: {} },
    data: null,
  }
  return err
}

function makeClient() {
  const csrfManager = createCsrfTokenManager()
  return createHttpClient({
    csrfManager,
    baseURL: 'http://test.local',
    getLocale: () => 'en-US',
    dedupPolicy: 'all-non-auth',
  })
}

describe('createHttpClient', () => {
  it('returns apiGet/apiPost/apiPatch/apiPut/apiDelete/apiUpload/apiDownload', () => {
    const client = makeClient()
    expect(typeof client.apiGet).toBe('function')
    expect(typeof client.apiPost).toBe('function')
    expect(typeof client.apiPatch).toBe('function')
    expect(typeof client.apiPut).toBe('function')
    expect(typeof client.apiDelete).toBe('function')
    expect(typeof client.apiUpload).toBe('function')
    expect(typeof client.apiDownload).toBe('function')
  })

  it('exposes the underlying axios instance', () => {
    const client = makeClient()
    expect(client.axiosInstance).toBeDefined()
    expect(typeof client.axiosInstance.get).toBe('function')
  })

  it('createAbortController returns a usable AbortController', () => {
    const client = makeClient()
    const ac = client.createAbortController()
    expect(ac).toBeInstanceOf(AbortController)
    expect(ac.signal.aborted).toBe(false)
  })
})

describe('ApiResponse unwrap', () => {
  it('unwraps { code: 0, data } into just the data value', async () => {
    const adapter = vi.fn().mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { code: 0, message: 'success', data: { id: 'u-1' }, traceId: 't-1' },
    })
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    const result = await client.apiGet<{ id: string }>('/users/me')
    expect(result).toEqual({ id: 'u-1' })
  })

  it('rejects with ApiError when code is non-zero', async () => {
    const adapter = vi.fn().mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { code: 1001, message: 'invalid', data: null },
    })
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    await expect(client.apiGet('/users/me')).rejects.toMatchObject({
      name: 'ApiError',
      code: 1001,
    })
  })
})

describe('Auth failure strategy', () => {
  it('invokes the clear-and-run callback on 401', async () => {
    const adapter = vi.fn().mockRejectedValue(buildAxiosError(401, 'Unauthorized'))
    const onAuthFailure = vi.fn()
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      onAuthFailure: { kind: 'clear-and-run', onAuthFailure },
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    await client.apiGet('/admin/foo').catch(() => {})
    expect(onAuthFailure).toHaveBeenCalled()
  })

  it('does NOT redirect on 403 in redirect-login mode (forbidden ≠ unauthenticated)', async () => {
    const adapter = vi.fn().mockRejectedValue(buildAxiosError(403, 'Forbidden'))
    const redirect = vi.fn()
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      onAuthFailure: { kind: 'redirect-login', onAuthFailure: redirect },
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    await client.apiGet('/admin/foo').catch(() => {})
    expect(redirect).not.toHaveBeenCalled()
  })

  it('invokes redirect-login on 401 with the configured path', async () => {
    const adapter = vi.fn().mockRejectedValue(buildAxiosError(401, 'Unauthorized'))
    const redirect = vi.fn()
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      onAuthFailure: { kind: 'redirect-login', onAuthFailure: redirect },
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    await client.apiGet('/admin/foo').catch(() => {})
    expect(redirect).toHaveBeenCalledWith('/login')
  })
})

describe('Dedup policy', () => {
  it("'all-non-auth' dedupes GET requests", async () => {
    let pending: ((value: unknown) => void) | null = null
    const adapter = vi.fn().mockImplementation(
      () =>
        new Promise<unknown>((resolve) => {
          pending = (value: unknown): void => {
            resolve(value)
          }
        }),
    )
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    const p1 = client.apiGet('/foo').catch(() => {})
    const p2 = client.apiGet('/foo').catch(() => {})
    await new Promise((r) => setTimeout(r, 5))
    // The Promise executor captured `pending` synchronously; assert non-null
    // since we just slept 5ms after firing both requests.
    expect(pending).not.toBeNull()
    pending!({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { code: 0, message: 'ok', data: { ok: true } },
    })
    await Promise.all([p1, p2])
    expect(adapter).toHaveBeenCalledTimes(1)
  })

  it("'non-auth-readonly' does NOT dedup PATCH/PUT/DELETE", async () => {
    const adapter = vi.fn().mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { code: 0, message: 'ok', data: null },
    })
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'non-auth-readonly',
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    await Promise.all([
      client.apiPatch('/foo/1', {}).catch(() => {}),
      client.apiPatch('/foo/1', {}).catch(() => {}),
    ])
    expect(adapter).toHaveBeenCalledTimes(2)
  })
})

describe('Retry / backoff', () => {
  it('does NOT retry when retry: 0 (verified via adapter call count)', async () => {
    const adapter = vi.fn().mockRejectedValue(buildAxiosError(500, 'Internal Server Error'))
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'none',
    })
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.axiosInstance.defaults as any).adapter = adapter

    await client.apiGet('/foo', { retry: 0 }).catch(() => {})
    expect(adapter).toHaveBeenCalledTimes(1)
  })
})