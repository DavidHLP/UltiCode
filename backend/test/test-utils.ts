/**
 * E2E Test Utilities
 *
 * Helper functions for E2E tests.
 */

/**
 * Generate a unique test email
 */
export function generateTestEmail(): string {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(7);
  return `test-${timestamp}-${random}@example.com`;
}

/**
 * Generate a unique test username
 */
export function generateTestUsername(): string {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(7);
  return `testuser_${timestamp}_${random}`;
}

/**
 * Wait for a condition to be true
 */
export async function waitFor(
  condition: () => Promise<boolean> | boolean,
  timeout = 5000,
  interval = 100,
): Promise<void> {
  const startTime = Date.now();

  while (Date.now() - startTime < timeout) {
    if (await condition()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, interval));
  }

  throw new Error(`Timeout waiting for condition after ${timeout}ms`);
}

/**
 * Helper to extract cookies from response
 */
export function extractCookies(response: any): Record<string, string> {
  const cookies: Record<string, string> = {};
  const cookieHeaders = response.headers['set-cookie'];

  if (cookieHeaders) {
    for (const header of cookieHeaders) {
      const parts = header.split(';')[0].split('=');
      if (parts.length === 2) {
        cookies[parts[0]] = parts[1];
      }
    }
  }

  return cookies;
}
