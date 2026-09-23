import { describe, expect, it } from 'vitest'
import { createValueRequest } from './index'

describe('createValueRequest', () => {
  it('returns only the newest response and keeps its loading ownership', async () => {
    const request = createValueRequest({ errorMessage: 'failed' })
    let resolveFirst!: (value: string) => void
    const first = request.run(() => new Promise<string>((resolve) => { resolveFirst = resolve }))
    const second = request.run(async () => 'newest')
    resolveFirst('stale')
    await expect(second).resolves.toBe('newest')
    await expect(first).resolves.toBeNull()
    expect(request.loading.value).toBe(false)
  })

  it('cancels the active request and ignores its late error', async () => {
    const request = createValueRequest({ errorMessage: 'failed' })
    let rejectLoad!: (error: Error) => void
    let signal!: AbortSignal
    const pending = request.run((currentSignal) => {
      signal = currentSignal
      return new Promise<string>((_resolve, reject) => { rejectLoad = reject })
    })
    request.cancel()
    expect(signal.aborted).toBe(true)
    rejectLoad(new Error('late'))
    await expect(pending).resolves.toBeNull()
    expect(request.error.value).toBeNull()
    expect(request.loading.value).toBe(false)
  })

  it('records current errors and rethrows when requested', async () => {
    const request = createValueRequest({ errorMessage: 'failed', rethrow: true })
    const failure = new Error('request failed')
    await expect(request.run(async () => { throw failure })).rejects.toBe(failure)
    expect(request.error.value).toBe('request failed')
    expect(request.loading.value).toBe(false)
  })
})
