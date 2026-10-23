/** Serialize cookie changes across tabs before allowing the next account operation. */
export function withBrowserSessionLock<T>(
  action: () => Promise<T>,
): Promise<T> {
  // shortcut: Web Locks cover one origin; cross-origin deployments need server coordination.
  if (typeof navigator !== "undefined" && navigator.locks) {
    return navigator.locks.request<T>("ulticode-browser-session", action);
  }
  return action();
}
