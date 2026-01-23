import type { Request } from 'express';

/**
 * Extract JWT token from Authorization header
 * @param request Express request object
 * @returns The token string or undefined if not found
 */
export function extractTokenFromHeader(request: Request): string | undefined {
  const [type, token] = (request.headers.authorization?.split(' ') ?? []) as [
    string | undefined,
    string | undefined,
  ];
  return type === 'Bearer' ? token : undefined;
}
