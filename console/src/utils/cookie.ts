/**
 * Cookie parsing utilities
 *
 * Local copy for console frontend to avoid TypeScript project reference issues.
 * Source: shared/auth-core/src/cookie.ts
 */

/**
 * Parse a cookie string into a Map of name → value.
 */
export function parseCookies(cookieString: string): Map<string, string> {
  const map = new Map<string, string>();

  if (!cookieString || typeof cookieString !== 'string') {
    return map;
  }

  cookieString.split(';').forEach((pair) => {
    const idx = pair.indexOf('=');
    const name = idx >= 0 ? pair.slice(0, idx).trim() : pair.trim();
    const value = idx >= 0 ? pair.slice(idx + 1).trim() : '';

    if (name) {
      map.set(name, value);
    }
  });

  return map;
}

/**
 * Check whether a cookie with the given exact name exists.
 */
export function hasCookie(cookies: Map<string, string>, name: string): boolean {
  if (!name || typeof name !== 'string') return false;
  return cookies.has(name);
}

/**
 * Get the value of a cookie by exact name.
 */
export function getCookie(cookies: Map<string, string>, name: string): string | undefined {
  if (!name || typeof name !== 'string') return undefined;
  return cookies.get(name);
}
