import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { useAutoSave } from './useAutoSave'

describe('useAutoSave', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('should initialize with idle status', () => {
    const saveFn = vi.fn().mockResolvedValue(undefined)
    const { saveStatus, lastSavedAt, error } = useAutoSave(saveFn)

    expect(saveStatus.value).toBe('idle')
    expect(lastSavedAt.value).toBeNull()
    expect(error.value).toBeNull()
  })

  it('should debounce save calls', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined)
    const { save, saveStatus } = useAutoSave(saveFn, { debounceMs: 500 })

    save('data1')
    expect(saveStatus.value).toBe('idle')

    vi.advanceTimersByTime(500)
    await nextTick()

    expect(saveFn).toHaveBeenCalledTimes(1)
    expect(saveFn).toHaveBeenCalledWith('data1', expect.any(AbortSignal))
    expect(saveStatus.value).toBe('saved')
  })

  it('should cancel previous save when new save starts', async () => {
    const saveFn = vi.fn().mockImplementation(async (data, signal) => {
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => resolve(undefined), 1000)
        signal.addEventListener('abort', () => {
          clearTimeout(timeout)
          reject(new DOMException('Aborted', 'AbortError'))
        })
      })
    })

    const { save, saveStatus } = useAutoSave(saveFn, { debounceMs: 100 })

    save('data1')
    vi.advanceTimersByTime(100)
    await nextTick()

    expect(saveStatus.value).toBe('saving')

    save('data2')
    vi.advanceTimersByTime(100)
    await nextTick()

    expect(saveFn).toHaveBeenCalledTimes(2)
    expect(saveStatus.value).toBe('saving')
  })

  it('should set error status on save failure', async () => {
    const testError = new Error('Save failed')
    const saveFn = vi.fn().mockRejectedValue(testError)
    const { save, saveStatus, error } = useAutoSave(saveFn, { debounceMs: 100 })

    save('data')
    vi.advanceTimersByTime(100)
    await nextTick()

    expect(saveStatus.value).toBe('error')
    expect(error.value).toBe(testError)
  })

  it('should update lastSavedAt on successful save', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined)
    const { save, lastSavedAt } = useAutoSave(saveFn, { debounceMs: 100 })

    const beforeSave = new Date()
    save('data')
    vi.advanceTimersByTime(100)
    await nextTick()

    expect(lastSavedAt.value).not.toBeNull()
    expect(lastSavedAt.value!.getTime()).toBeGreaterThanOrEqual(beforeSave.getTime())
  })

  it('should cancel in-flight request when cancel is called', async () => {
    const saveFn = vi.fn().mockImplementation(async (data, signal) => {
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => resolve(undefined), 1000)
        signal.addEventListener('abort', () => {
          clearTimeout(timeout)
          reject(new DOMException('Aborted', 'AbortError'))
        })
      })
    })

    const { save, cancel, saveStatus } = useAutoSave(saveFn, { debounceMs: 100 })

    save('data')
    vi.advanceTimersByTime(100)
    await nextTick()

    expect(saveStatus.value).toBe('saving')

    cancel()
    await nextTick()

    expect(saveStatus.value).toBe('idle')
  })

  it('should bypass debounce when blurTriggers is false', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined)
    const { save, saveStatus } = useAutoSave(saveFn, { debounceMs: 500, blurTriggers: false })

    save('data')
    await nextTick()

    expect(saveStatus.value).toBe('saved')
    expect(saveFn).toHaveBeenCalledTimes(1)
  })

  it('should not treat abort as error', async () => {
    const saveFn = vi.fn().mockImplementation(async (data, signal) => {
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => resolve(undefined), 1000)
        signal.addEventListener('abort', () => {
          clearTimeout(timeout)
          reject(new DOMException('Aborted', 'AbortError'))
        })
      })
    })

    const { save, cancel, saveStatus, error } = useAutoSave(saveFn, { debounceMs: 100 })

    save('data')
    vi.advanceTimersByTime(100)
    await nextTick()

    cancel()
    await nextTick()

    expect(saveStatus.value).toBe('idle')
    expect(error.value).toBeNull()
  })

  it('should use default debounce of 1000ms', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined)
    const { save } = useAutoSave(saveFn)

    save('data')
    expect(saveFn).not.toHaveBeenCalled()

    vi.advanceTimersByTime(999)
    expect(saveFn).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    await nextTick()

    expect(saveFn).toHaveBeenCalledTimes(1)
  })

  it('should pass AbortSignal to saveFn', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined)
    const { save } = useAutoSave(saveFn, { debounceMs: 100 })

    save('data')
    vi.advanceTimersByTime(100)
    await nextTick()

    expect(saveFn).toHaveBeenCalledWith('data', expect.any(AbortSignal))
  })
})
