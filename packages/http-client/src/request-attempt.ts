/**
 * Own the current Axios attempt for each deduplicated request key.
 *
 * A request can outlive its ownership when a newer same-key request replaces
 * it. Keeping that identity here prevents late responses, errors, and retry
 * continuations from mutating the replacement attempt.
 */

export interface RequestAttempt {
  readonly signal?: AbortSignal
  dispose(): void
}

interface AttemptRecord extends RequestAttempt {
  readonly controller: AbortController
  readonly supersede: () => void
}

interface BeginAttemptOptions {
  key: string
  deduplicated: boolean
  retrying: boolean
  previousSignal?: AbortSignal
  callerSignal?: AbortSignal
}

function linkCallerSignal(controller: AbortController, callerSignal?: AbortSignal): () => void {
  if (!callerSignal) return () => undefined

  const onCallerAbort = (): void => controller.abort()
  if (callerSignal.aborted) {
    controller.abort()
    return () => undefined
  }

  callerSignal.addEventListener('abort', onCallerAbort, { once: true })
  return () => callerSignal.removeEventListener('abort', onCallerAbort)
}

function createAttempt(callerSignal?: AbortSignal): AttemptRecord {
  const controller = new AbortController()
  let disposed = false
  const unlinkCallerSignal = linkCallerSignal(controller, callerSignal)
  const dispose = (): void => {
    if (disposed) return
    disposed = true
    unlinkCallerSignal()
  }

  return {
    controller,
    signal: controller.signal,
    dispose,
    supersede: () => {
      controller.abort()
      dispose()
    },
  }
}

function createUntrackedAttempt(callerSignal?: AbortSignal): RequestAttempt {
  return {
    signal: callerSignal,
    dispose: () => undefined,
  }
}

export class RequestAttemptRegistry {
  private readonly currentAttempts = new Map<string, AttemptRecord>()
  private readonly untrackedAttempts = new WeakSet<RequestAttempt>()

  begin(options: BeginAttemptOptions): RequestAttempt {
    if (!options.deduplicated) {
      const attempt = createUntrackedAttempt(options.callerSignal)
      this.untrackedAttempts.add(attempt)
      return attempt
    }

    const attempt = createAttempt(options.callerSignal)
    const current = this.currentAttempts.get(options.key)
    const mayClaim = !options.retrying || current?.signal === options.previousSignal

    if (mayClaim) {
      current?.supersede()
      this.currentAttempts.set(options.key, attempt)
    }

    return attempt
  }

  owns(key: string, attempt?: RequestAttempt): boolean {
    if (!attempt) return false
    return this.untrackedAttempts.has(attempt) || this.currentAttempts.get(key) === attempt
  }

  complete(key: string, attempt: RequestAttempt): void {
    if (this.currentAttempts.get(key) === attempt) {
      this.currentAttempts.delete(key)
    }
    attempt.dispose()
  }
}
