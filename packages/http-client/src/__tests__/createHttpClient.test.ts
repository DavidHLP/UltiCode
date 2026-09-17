import { describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createCsrfTokenManager } from '@ulticode/auth-core/src/csrf'
import { createHttpClient, type HttpClient, type RequestConfig } from '../index'

/**
 * Build an AxiosError with the given HTTP status. Axios only invokes the
 * error interceptor when the adapter throws an error — a `resolve()` with a
 * 401 status counts as a successful HTTP response, so we need to fabricate
 * the error envelope ourselves to exercise the auth-failure and retry paths.
 */
function buildAxiosError(
  status: number,
  message: string,
  request?: InternalAxiosRequestConfig,
): AxiosError {
  const err = new AxiosError(message, undefined, request)
  Object.assign(err, {
    response: {
      status,
      statusText: message,
      headers: {},
      config: request ?? { headers: {} },
      data: null,
    },
  })
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

function createPendingAdapter() {
  const pending: Array<(value: unknown) => void> = []
  const requests: Array<{ signal?: AbortSignal }> = []
  const adapter = vi.fn().mockImplementation(
    (request: { signal?: AbortSignal }) =>
      new Promise<unknown>((resolve, reject) => {
        requests.push(request)
        request.signal?.addEventListener(
          'abort',
          () => reject(new axios.CanceledError()),
          { once: true },
        )
        pending.push(resolve)
      }),
  )
  return { adapter, pending, requests }
}
type PendingRequest = {
  request: InternalAxiosRequestConfig
  resolve: (value: unknown) => void
  reject: (reason?: unknown) => void
}

function createRaceAdapter() {
  const requests: PendingRequest[] = []
  const adapter = vi.fn().mockImplementation((request: InternalAxiosRequestConfig) =>
    new Promise<unknown>((resolve, reject) => {
      requests.push({ request, resolve, reject })
    }),
  )
  return { adapter, requests }
}

function responseFor(request: InternalAxiosRequestConfig) {
  return {
    status: 200,
    statusText: 'OK',
    headers: {},
    config: request,
    data: { code: 0, message: 'ok', data: { ok: true } },
  }
}

function canceledErrorFor(request: InternalAxiosRequestConfig): AxiosError {
  return new AxiosError('canceled', 'ERR_CANCELED', request)
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
      __testAdapter: adapter,
    })

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
      __testAdapter: adapter,
    })

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
      __testAdapter: adapter,
    })

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
      __testAdapter: adapter,
    })

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
      __testAdapter: adapter,
    })

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
      __testAdapter: adapter,
    })

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

  it('isolates deduplication and cancellation between client instances', async () => {
    const clientAState = createPendingAdapter()
    const clientBState = createPendingAdapter()
    const clientA = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: clientAState.adapter,
    })
    const clientB = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: clientBState.adapter,
    })

    const firstA = clientA.apiGet('/foo').catch((error: unknown) => error)
    const firstB = clientB.apiGet('/foo').catch((error: unknown) => error)
    await vi.waitFor(() => {
      expect(clientAState.adapter).toHaveBeenCalledTimes(1)
      expect(clientBState.adapter).toHaveBeenCalledTimes(1)
    })
    const secondA = clientA.apiGet('/foo')
    await vi.waitFor(() => expect(clientAState.adapter).toHaveBeenCalledTimes(2))

    expect(clientAState.adapter).toHaveBeenCalledTimes(2)
    expect(clientBState.adapter).toHaveBeenCalledTimes(1)
    expect(clientAState.requests[0].signal?.aborted).toBe(true)
    expect(clientBState.requests[0].signal?.aborted).toBe(false)

    const response = {
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { code: 0, message: 'ok', data: { ok: true } },
    }
    clientAState.pending[1](response)
    clientBState.pending[0](response)

    await expect(firstA).resolves.toMatchObject({ name: 'ApiError', code: -1 })
    await expect(secondA).resolves.toEqual({ ok: true })
    await expect(firstB).resolves.toEqual({ ok: true })
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
      __testAdapter: adapter,
    })

    await Promise.all([
      client.apiPatch('/foo/1', {}).catch(() => {}),
      client.apiPatch('/foo/1', {}).catch(() => {}),
    ])
    expect(adapter).toHaveBeenCalledTimes(2)
  })

  it("'non-auth-readonly' does NOT dedup POST", async () => {
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
      __testAdapter: adapter,
    })

    await Promise.all([
      client.apiPost('/foo', {}).catch(() => {}),
      client.apiPost('/foo', {}).catch(() => {}),
    ])
    expect(adapter).toHaveBeenCalledTimes(2)
  })
  it('forwards the package-owned AbortSignal option through every helper signature', async () => {
    const seenSignals: unknown[] = []
    const adapter = vi.fn().mockImplementation((request: InternalAxiosRequestConfig) => {
      seenSignals.push(request.signal)
      return Promise.resolve(responseFor(request))
    })
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'none',
      __testAdapter: adapter,
    })
    const controller = new AbortController()
    const init: RequestConfig = { signal: controller.signal }

    await client.apiGet('/signal', init)
    await client.apiPost('/signal', {}, init)
    await client.apiPatch('/signal', {}, init)
    await client.apiPut('/signal', {}, init)
    await client.apiDelete('/signal', init)
    await client.apiUpload('/signal', new Blob(['payload']), undefined, init)
    const download: HttpClient['apiDownload'] = client.apiDownload
    const downloadWithSignal = (
      path: string,
      filename: string,
      downloadInit: RequestConfig,
    ): Promise<void> => client.apiDownload(path, filename, downloadInit)

    expect(typeof download).toBe('function')
    expect(seenSignals).toEqual(Array.from({ length: 6 }, () => controller.signal))
  })

  it('does not let a stale response clear the replacement request entry', async () => {
    const state = createRaceAdapter()
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: state.adapter,
    })

    const first = client.apiGet('/race').catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(1))
    const second = client.apiGet('/race').catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(2))
    expect(state.requests[0].request.signal?.aborted).toBe(true)

    // Let Axios deliver a response from the already-aborted adapter call.
    state.requests[0].request.signal = new AbortController().signal
    state.requests[0].resolve(responseFor(state.requests[0].request))
    await expect(first).resolves.toEqual({ ok: true })

    const third = client.apiGet('/race').catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(3))
    expect(state.requests[1].request.signal?.aborted).toBe(true)

    state.requests[1].reject(canceledErrorFor(state.requests[1].request))
    state.requests[2].resolve(responseFor(state.requests[2].request))
    await expect(second).resolves.toMatchObject({ name: 'ApiError', code: -1 })
    await expect(third).resolves.toEqual({ ok: true })
  })

  it('does not let a stale error clear the replacement request entry', async () => {
    const state = createRaceAdapter()
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: state.adapter,
    })

    const first = client.apiGet('/race').catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(1))
    const second = client.apiGet('/race').catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(2))
    state.requests[0].reject(canceledErrorFor(state.requests[0].request))
    await expect(first).resolves.toMatchObject({ name: 'ApiError', code: -1 })

    const third = client.apiGet('/race').catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(3))
    expect(state.requests[1].request.signal?.aborted).toBe(true)

    state.requests[1].reject(canceledErrorFor(state.requests[1].request))
    state.requests[2].resolve(responseFor(state.requests[2].request))
    await expect(second).resolves.toMatchObject({ name: 'ApiError', code: -1 })
    await expect(third).resolves.toEqual({ ok: true })
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
      __testAdapter: adapter,
    })

    await client.apiGet('/foo', { retry: 0 }).catch(() => {})
    expect(adapter).toHaveBeenCalledTimes(1)
  })
  it('stops retrying after the configured limit when requests keep failing', async () => {
    const adapter = vi.fn().mockImplementation((request: InternalAxiosRequestConfig) =>
      Promise.reject(buildAxiosError(500, 'Internal Server Error', request)),
    )
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'none',
      __testAdapter: adapter,
    })

    await expect(client.apiGet('/foo', { retry: 1, retryDelay: 1 })).rejects.toMatchObject({
      name: 'ApiError',
      code: 500,
    })
    expect(adapter).toHaveBeenCalledTimes(2)
  })
  it('does not let a retry abort a newer same-key request', async () => {
    const pending: Array<{
      request: InternalAxiosRequestConfig
      resolve: (value: unknown) => void
    }> = []
    let firstRequest: InternalAxiosRequestConfig | undefined
    let rejectFirst: (reason?: unknown) => void = () => undefined
    const firstFailure = new Promise<unknown>((_, reject) => {
      rejectFirst = reject
    })
    const adapter = vi.fn().mockImplementation((request: InternalAxiosRequestConfig) => {
      if (adapter.mock.calls.length === 1) {
        firstRequest = request
        return firstFailure
      }
      return new Promise<unknown>((resolve) => {
        pending.push({ request, resolve })
      })
    })
    const client = createHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: adapter,
    })

    const first = client.apiGet('/retry-race', { retry: 1, retryDelay: 20 })
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(1))
    const second = client.apiGet('/retry-race')
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(2))

    if (!firstRequest) throw new Error('first request was not captured')
    rejectFirst(buildAxiosError(500, 'retry me', firstRequest))
    pending[0].resolve(responseFor(pending[0].request))
    await expect(second).resolves.toEqual({ ok: true })
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(3))

    expect(pending[0].request.signal?.aborted).toBe(false)
    pending[1].resolve(responseFor(pending[1].request))
    await expect(first).resolves.toEqual({ ok: true })
  })
})