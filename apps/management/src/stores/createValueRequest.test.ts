import { describe, expect, it } from 'vitest'
import { createValueRequest } from './createValueRequest'

describe('createValueRequest', () => {
  it('drops a stale response when a newer request starts', async () => {
    const request = createValueRequest({ errorMessage: 'Failed to load value' })
    let resolveFirst!: (value: string) => void
    const first = request.run(() => new Promise<string>((resolve) => {
      resolveFirst = resolve
    }))

    const second = request.run(async () => 'newer')
    resolveFirst('older')

    await expect(second).resolves.toBe('newer')
    await expect(first).resolves.toBeNull()
    expect(request.loading.value).toBe(false)
  })

  it('clears loading and aborts the active request on cancel', async () => {
    const request = createValueRequest({ errorMessage: 'Failed to load value' })
    let signal!: AbortSignal
    const pending = request.run((requestSignal) => {
      signal = requestSignal
      return new Promise<string>((_resolve, reject) => {
        requestSignal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), {
          once: true,
        })
      })
    })

    request.cancel()

    expect(signal.aborted).toBe(true)
    expect(request.loading.value).toBe(false)
    await expect(pending).resolves.toBeNull()
  })
})
