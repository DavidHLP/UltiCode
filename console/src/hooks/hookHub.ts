export type HookHandler<T> = (payload: T) => void | Promise<void>;

export interface HookHub<T extends Record<string, unknown>> {
  on<K extends keyof T>(event: K, handler: HookHandler<T[K]>): () => void;
  emit<K extends keyof T>(event: K, payload: T[K]): Promise<void>;
  clear(event?: keyof T): void;
}

interface HookHubOptions<T extends Record<string, unknown>> {
  name?: string;
  onError?: (event: keyof T, error: unknown) => void;
}

export const createHookHub = <T extends Record<string, unknown>>(
  options: HookHubOptions<T> = {},
): HookHub<T> => {
  const name = options.name ?? "hooks";
  const onError =
    options.onError ??
    ((event, error) => {
      console.error(`[${name}] hook "${String(event)}" failed`, error);
    });

  const handlers = new Map<keyof T, Set<HookHandler<T[keyof T]>>>();

  const on = <K extends keyof T>(event: K, handler: HookHandler<T[K]>) => {
    let set = handlers.get(event);
    if (!set) {
      set = new Set();
      handlers.set(event, set);
    }
    set.add(handler as HookHandler<T[keyof T]>);
    return () => set?.delete(handler as HookHandler<T[keyof T]>);
  };

  const emit = async <K extends keyof T>(event: K, payload: T[K]) => {
    const set = handlers.get(event);
    if (!set || set.size === 0) return;
    const queue = Array.from(set);
    for (const handler of queue) {
      try {
        await handler(payload);
      } catch (error) {
        onError(event, error);
      }
    }
  };

  const clear = (event?: keyof T) => {
    if (event) {
      handlers.delete(event);
      return;
    }
    handlers.clear();
  };

  return { on, emit, clear };
};
