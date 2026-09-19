import { afterEach, describe, expect, it, vi } from 'vitest'
import axios, {
  AxiosError,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from 'axios'
import { createCsrfTokenManager } from '@ulticode/auth-core/src/csrf'
import {
  createHttpClient,
  type HttpClient,
  type HttpClientConfig,
  type RequestConfig,
} from '../index'

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

type TestHttpClientConfig = HttpClientConfig & { __testAdapter: unknown }

function createTestHttpClient(config: TestHttpClientConfig): HttpClient {
  const { __testAdapter, ...clientConfig } = config
  const originalCreate = axios.create
  const createSpy = vi.spyOn(axios, 'create')
  createSpy.mockImplementation((defaults) => {
    const service = originalCreate(defaults)
    service.defaults.adapter = __testAdapter as AxiosAdapter
    return service
  })
  try {
    return createHttpClient(clientConfig)
  } finally {
    createSpy.mockRestore()
  }
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

interface DownloadAnchor {
  href: string
  attributes: Record<string, string>
  attached: number
  clicked: number
  removeAttempts: number
  removed: number
  setAttribute(name: string, value: string): void
  click(): void
  remove(): void
}

interface DownloadDom {
  anchors: DownloadAnchor[]
  createObjectURL: ReturnType<typeof vi.fn>
  revokeObjectURL: ReturnType<typeof vi.fn>
  failOn: { append?: boolean; click?: boolean; remove?: boolean }
}

/** Minimal browser stand-ins for the download flow; restored via vi.unstubAllGlobals(). */
function installDownloadDom(): DownloadDom {
  const dom: DownloadDom = {
    anchors: [],
    createObjectURL: vi.fn(),
    revokeObjectURL: vi.fn(),
    failOn: {},
  }
  let generated = 0
  dom.createObjectURL.mockImplementation(() => {
    generated += 1
    return `blob:download-${generated}`
  })
  const makeAnchor = (): DownloadAnchor => {
    const anchor: DownloadAnchor = {
      href: '',
      attributes: {},
      attached: 0,
      clicked: 0,
      removeAttempts: 0,
      removed: 0,
      setAttribute(name: string, value: string) {
        anchor.attributes[name] = value
      },
      click() {
        if (dom.failOn.click) throw new Error('click-boom')
        anchor.clicked += 1
      },
      remove() {
        anchor.removeAttempts += 1
        if (dom.failOn.remove) throw new Error('remove-boom')
        anchor.removed += 1
      },
    }
    return anchor
  }
  vi.stubGlobal('window', {
    URL: { createObjectURL: dom.createObjectURL, revokeObjectURL: dom.revokeObjectURL },
  })
  vi.stubGlobal('document', {
    cookie: '',
    createElement: () => {
      const anchor = makeAnchor()
      dom.anchors.push(anchor)
      return anchor
    },
    body: {
      appendChild: (node: DownloadAnchor) => {
        if (dom.failOn.append) throw new Error('append-boom')
        node.attached += 1
        return node
      },
    },
  })
  return dom
}

function succeedWithBlob(blob: Blob, capture: { request?: InternalAxiosRequestConfig }) {
  return vi.fn().mockImplementation((request: InternalAxiosRequestConfig) => {
    capture.request = request
    return Promise.resolve({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: request,
      data: blob,
    })
  })
}

function downloadClient(adapter: unknown): HttpClient {
  return createTestHttpClient({
    csrfManager: createCsrfTokenManager(),
    baseURL: 'http://test.local',
    getLocale: () => 'en-US',
    dedupPolicy: 'none',
    __testAdapter: adapter,
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

  it('createAbortController returns a usable AbortController', () => {
    const client = makeClient()
    const ac = client.createAbortController()
    expect(ac).toBeInstanceOf(AbortController)
    expect(ac.signal.aborted).toBe(false)
  })
  it('keeps adapter injection outside the public client config', () => {
    const config: HttpClientConfig = {
      csrfManager: createCsrfTokenManager(),
      getLocale: () => 'en-US',
      // @ts-expect-error Test adapters are wired through the test-only factory.
      __testAdapter: vi.fn(),
    }
    expect(config).toBeDefined()
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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

  it('returns a non-Result payload as-is without any transport envelope fields', async () => {
    const adapter = vi.fn().mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { direct: true },
    })
    const client = createTestHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'none',
      __testAdapter: adapter,
    })

    const result = await client.apiGet<Record<string, unknown>>('/plain')
    expect(result).toEqual({ direct: true })
    expect(result).not.toHaveProperty('status')
    expect(result).not.toHaveProperty('config')
  })

  it('returns a Blob payload unwrapped', async () => {
    const blob = new Blob(['bytes'])
    const adapter = vi.fn().mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: blob,
    })
    const client = createTestHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'none',
      __testAdapter: adapter,
    })

    const result = await client.apiGet<Blob>('/blob')
    expect(result).toBe(blob)
  })

  it('rejects the removed skipResponseUnwrap option at the type level', () => {
    const config: RequestConfig = {
      // @ts-expect-error skipResponseUnwrap is no longer part of the public request config.
      skipResponseUnwrap: true,
    }
    expect(config).toBeDefined()
  })

  it('ignores a legacy skipResponseUnwrap flag passed through an untyped config', async () => {
    const adapter = vi.fn().mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
      data: { direct: true },
    })
    const client = createTestHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'none',
      __testAdapter: adapter,
    })
    const legacyInit = { skipResponseUnwrap: true } as unknown as RequestConfig

    const result = await client.apiGet<Record<string, unknown>>('/legacy', legacyInit)
    expect(result).toEqual({ direct: true })
    expect(result).not.toHaveProperty('data')
  })
})

describe('Auth failure strategy', () => {
  it('invokes the clear-and-run callback on 401', async () => {
    const adapter = vi.fn().mockRejectedValue(buildAxiosError(401, 'Unauthorized'))
    const onAuthFailure = vi.fn()
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const clientA = createTestHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: clientAState.adapter,
    })
    const clientB = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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

  it('propagates caller abort and releases the current attempt', async () => {
    const state = createPendingAdapter()
    const client = createTestHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: state.adapter,
    })
    const controller = new AbortController()

    const first = client
      .apiGet('/abort', { signal: controller.signal })
      .catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(1))

    controller.abort()
    await expect(first).resolves.toMatchObject({ name: 'ApiError', code: -1 })

    const second = client.apiGet('/abort')
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(2))
    expect(state.requests[1].signal?.aborted).toBe(false)
    state.pending[1](responseFor(state.requests[1] as InternalAxiosRequestConfig))
    await expect(second).resolves.toEqual({ ok: true })
  })

  it('removes caller abort listeners when replacement supersedes an attempt', async () => {
    const state = createRaceAdapter()
    const client = createTestHttpClient({
      csrfManager: createCsrfTokenManager(),
      baseURL: 'http://test.local',
      getLocale: () => 'en-US',
      dedupPolicy: 'all-non-auth',
      __testAdapter: state.adapter,
    })
    const controller = new AbortController()
    const removeCallerListener = vi.spyOn(controller.signal, 'removeEventListener')

    const first = client
      .apiGet('/replacement-cleanup', { signal: controller.signal })
      .catch((error: unknown) => error)
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(1))

    const second = client.apiGet('/replacement-cleanup')
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(2))
    expect(removeCallerListener).toHaveBeenCalledTimes(1)

    controller.abort()
    expect(state.requests[1].request.signal?.aborted).toBe(false)
    state.requests[0].request.signal = new AbortController().signal
    state.requests[0].resolve(responseFor(state.requests[0].request))
    state.requests[1].resolve(responseFor(state.requests[1].request))

    await expect(first).resolves.toEqual({ ok: true })
    await expect(second).resolves.toEqual({ ok: true })
  })

})

describe('Retry / backoff', () => {
  it('does NOT retry when retry: 0 (verified via adapter call count)', async () => {
    const adapter = vi.fn().mockRejectedValue(buildAxiosError(500, 'Internal Server Error'))
    const client = createTestHttpClient({
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
    const client = createTestHttpClient({
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
  it('does not let a stale retry abort a newer same-key request', async () => {
    vi.useFakeTimers()
    const debug = vi.spyOn(console, 'debug')
    try {
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
      const client = createTestHttpClient({
        csrfManager: createCsrfTokenManager(),
        baseURL: 'http://test.local',
        getLocale: () => 'en-US',
        dedupPolicy: 'all-non-auth',
        __testAdapter: adapter,
      })

      const first = client
        .apiGet('/retry-race', { retry: 1, retryDelay: 100 })
        .catch((error: unknown) => error)
      await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(1))

      if (!firstRequest) throw new Error('first request was not captured')
      rejectFirst(buildAxiosError(500, 'retry me', firstRequest))
      await vi.advanceTimersByTimeAsync(0)

      const second = client.apiGet('/retry-race')
      await vi.waitFor(() => expect(pending).toHaveLength(1))
      expect(pending[0].request.signal?.aborted).toBe(false)

      await vi.advanceTimersByTimeAsync(100)
      expect(adapter).toHaveBeenCalledTimes(2)
      pending[0].resolve(responseFor(pending[0].request))

      await expect(second).resolves.toEqual({ ok: true })
      await expect(first).resolves.toMatchObject({ name: 'ApiError', code: 500 })
      expect(debug.mock.calls.filter(([message]) => message === '[API Retry]')).toHaveLength(0)
    } finally {
      debug.mockRestore()
      vi.useRealTimers()
    }
  })

  it('does not send another request when the caller aborts during backoff', async () => {
    vi.useFakeTimers()
    try {
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
        return Promise.reject(buildAxiosError(500, 'should never be sent', request))
      })
      const client = createTestHttpClient({
        csrfManager: createCsrfTokenManager(),
        baseURL: 'http://test.local',
        getLocale: () => 'en-US',
        dedupPolicy: 'all-non-auth',
        __testAdapter: adapter,
      })
      const controller = new AbortController()

      const first = client
        .apiGet('/backoff-abort', { retry: 1, retryDelay: 100, signal: controller.signal })
        .catch((error: unknown) => error)
      await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(1))

      if (!firstRequest) throw new Error('first request was not captured')
      rejectFirst(buildAxiosError(500, 'retry me', firstRequest))
      await vi.advanceTimersByTimeAsync(0)

      controller.abort()
      await vi.advanceTimersByTimeAsync(100)

      await expect(first).resolves.toMatchObject({ name: 'ApiError', code: -1 })
      expect(adapter).toHaveBeenCalledTimes(1)
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('apiDownload', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('passes the adapter Blob to createObjectURL and preserves filename, params and timeout', async () => {
    const blob = new Blob(['file-bytes'], { type: 'application/octet-stream' })
    const capture: { request?: InternalAxiosRequestConfig } = {}
    const dom = installDownloadDom()
    const client = downloadClient(succeedWithBlob(blob, capture))

    await client.apiDownload('/files/1', 'report.csv', {
      params: { scope: 'full' },
      timeout: 5000,
    })

    expect(capture.request?.params).toEqual({ scope: 'full' })
    expect(capture.request?.timeout).toBe(5000)
    expect(dom.createObjectURL).toHaveBeenCalledTimes(1)
    expect(dom.createObjectURL.mock.calls[0][0]).toBe(blob)
    expect(dom.revokeObjectURL).toHaveBeenCalledWith('blob:download-1')
    const [anchor] = dom.anchors
    expect(anchor.attributes.download).toBe('report.csv')
    expect(anchor.href).toBe('blob:download-1')
    expect(anchor.attached).toBe(1)
    expect(anchor.clicked).toBe(1)
    expect(anchor.removed).toBe(1)
  })

  it('falls back to the default download filename', async () => {
    const blob = new Blob(['x'])
    const dom = installDownloadDom()
    const client = downloadClient(succeedWithBlob(blob, {}))

    await client.apiDownload('/files/2')

    expect(dom.anchors[0].attributes.download).toBe('download')
    expect(dom.revokeObjectURL).toHaveBeenCalledTimes(1)
  })

  it('forces blob responseType even when the caller requests another one', async () => {
    const blob = new Blob(['x'])
    const capture: { request?: InternalAxiosRequestConfig } = {}
    const dom = installDownloadDom()
    const client = downloadClient(succeedWithBlob(blob, capture))

    await client.apiDownload('/files/3', 'f.bin', { responseType: 'text' })

    expect(capture.request?.responseType).toBe('blob')
    expect(dom.createObjectURL.mock.calls[0][0]).toBe(blob)
  })

  it('forwards the caller signal and skips all download DOM work on cancellation', async () => {
    const state = createPendingAdapter()
    const dom = installDownloadDom()
    const client = downloadClient(state.adapter)
    const controller = new AbortController()

    const download = client.apiDownload('/files/4', 'slow.bin', { signal: controller.signal })
    await vi.waitFor(() => expect(state.adapter).toHaveBeenCalledTimes(1))
    expect(state.requests[0].signal).toBeDefined()

    controller.abort()
    await expect(download).rejects.toMatchObject({ name: 'ApiError', code: -1 })
    expect(state.requests[0].signal?.aborted).toBe(true)
    expect(dom.createObjectURL).not.toHaveBeenCalled()
    expect(dom.revokeObjectURL).not.toHaveBeenCalled()
    expect(dom.anchors).toHaveLength(0)
  })

  it('rejects transport failures without any download DOM side effects', async () => {
    const adapter = vi.fn().mockImplementation((request: InternalAxiosRequestConfig) =>
      Promise.reject(buildAxiosError(500, 'Internal Server Error', request)),
    )
    const dom = installDownloadDom()
    const client = downloadClient(adapter)

    await expect(client.apiDownload('/files/5', 'boom.bin', { retry: 0 })).rejects.toMatchObject({
      name: 'ApiError',
      code: 500,
    })
    expect(dom.createObjectURL).not.toHaveBeenCalled()
    expect(dom.revokeObjectURL).not.toHaveBeenCalled()
    expect(dom.anchors).toHaveLength(0)
  })

  it('still removes the element and revokes the URL when click throws, keeping the original error', async () => {
    const blob = new Blob(['x'])
    const dom = installDownloadDom()
    dom.failOn.click = true
    dom.failOn.remove = true
    const client = downloadClient(succeedWithBlob(blob, {}))

    await expect(client.apiDownload('/files/6', 'a.bin')).rejects.toThrow('click-boom')
    expect(dom.createObjectURL).toHaveBeenCalledTimes(1)
    expect(dom.anchors).toHaveLength(1)
    expect(dom.anchors[0].clicked).toBe(0)
    expect(dom.anchors[0].removeAttempts).toBe(1)
    expect(dom.revokeObjectURL).toHaveBeenCalledWith('blob:download-1')
  })

  it('surfaces a cleanup failure when the download itself succeeded', async () => {
    const blob = new Blob(['x'])
    const dom = installDownloadDom()
    dom.failOn.remove = true
    const client = downloadClient(succeedWithBlob(blob, {}))

    await expect(client.apiDownload('/files/7', 'b.bin')).rejects.toThrow('remove-boom')
    expect(dom.anchors[0].clicked).toBe(1)
    expect(dom.revokeObjectURL).toHaveBeenCalledWith('blob:download-1')
  })
})
