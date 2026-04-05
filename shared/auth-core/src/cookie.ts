// ---------------------------------------------------------------------------
// Precise Cookie Parsing
// ---------------------------------------------------------------------------
// Replaces fragile `document.cookie.split(";").some((c) =>
//   c.trim().startsWith("access_token="))` patterns with exact name matching
// that cannot be fooled by `access_token_extra=x`.
//
// Browsers fire the `CookieChangeEvent` on the document when cookies are
// set or removed. In Node.js / unit-test contexts `document` is undefined,
// so we parse from a raw `Cookie` header string instead.
// ---------------------------------------------------------------------------

/**
 * Parse a cookie string into a Map of name → value.
 * Handles values that contain `=` characters (e.g. JWT tokens).
 *
 * @example
 * const cookies = 'access_token=abc; access_token_extra=xyz; other=value';
 * const map = parseCookies(cookies);
 * map.get('access_token')          // 'abc'
 * map.has('access_token_extra')    // true
 * map.has('nonexistent')            // false
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
      // If the same name appears multiple times the last occurrence wins,
      // matching browser behaviour for `document.cookie` reads.
      map.set(name, value);
    }
  });

  return map;
}

/**
 * Check whether a cookie with the given exact name exists.
 * Uses exact string equality — `access_token` does NOT match `access_token_extra`.
 *
 * @example
 * const cookies = parseCookies('access_token=abc; access_token_extra=xyz');
 * hasCookie(cookies, 'access_token')         // true
 * hasCookie(cookies, 'access_token_extra')   // true
 * hasCookie(cookies, 'nonexistent')           // false
 */
export function hasCookie(cookies: Map<string, string>, name: string): boolean {
  if (!name || typeof name !== 'string') return false;
  return cookies.has(name);
}

/**
 * Get the value of a cookie by exact name.
 * Returns `undefined` if the cookie does not exist.
 *
 * @example
 * const cookies = parseCookies('access_token=abc');
 * getCookie(cookies, 'access_token')    // 'abc'
 * getCookie(cookies, 'nonexistent')      // undefined
 */
export function getCookie(cookies: Map<string, string>, name: string): string | undefined {
  if (!name || typeof name !== 'string') return undefined;
  return cookies.get(name);
}

/**
 * Build a `Cookie` header string from a Map.
 * Useful for server-side code that needs to echo cookies back.
 *
 * @example
 * const map = new Map([['access_token', 'abc'], ['other', 'val']]);
 * buildCookieHeader(map)   // 'access_token=abc; other=val'
 */
export function buildCookieHeader(cookies: Map<string, string>): string {
  const parts: string[] = [];
  cookies.forEach((value, name) => {
    // Cookie values should be encoded, but we leave that to the caller.
    parts.push(`${name}=${value}`);
  });
  return parts.join('; ');
}
