// Diagnostic booleans only; never use this probe to authenticate a request.
import { createHmac, timingSafeEqual } from 'node:crypto'

export function summarizeAccessCookie(cookieHeader, options = {}) {
  const cookie = name => {
    const match = cookieHeader.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]*)'))
    try { return match ? decodeURIComponent(match[1]) : '' } catch { return '' }
  }
  const token = cookie('access_token')
  const csrf = cookie('csrf_token')
  const result = {
    tokenPresent: Boolean(token), parsed: false, matchesProtectedPreflight: false,
    subjectMatchesProtectedActor: false, hmacApplicable: false, hmacMatchesConfiguredSecret: false,
    issuerMatchesExpected: false, audienceMatchesExpected: false, timeClaimsValid: false,
    roleIsOrdinaryUser: false, csrfHeaderPresent: Boolean(options.csrfHeader),
    csrfCookiePresent: Boolean(csrf), csrfHeaderMatchesCookie: Boolean(csrf) && csrf === options.csrfHeader,
  }
  try {
    if (token.length > 16384) return result
    const parts = token.split('.')
    if (parts.length !== 3 || !parts.every(part => /^[A-Za-z0-9_-]+$/.test(part))) return result
    const header = JSON.parse(Buffer.from(parts[0], 'base64url').toString())
    const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString())
    result.parsed = true
    result.matchesProtectedPreflight = Boolean(options.preflightToken) && token === options.preflightToken
    result.subjectMatchesProtectedActor = Boolean(options.expectedActorId) && claims.sub === options.expectedActorId
    result.hmacApplicable = header.alg === 'HS256'
    if (result.hmacApplicable && typeof options.secret === 'string' && options.secret.length >= 32) {
      const expected = createHmac('sha256', options.secret).update(parts[0] + '.' + parts[1]).digest()
      const signature = Buffer.from(parts[2], 'base64url')
      result.hmacMatchesConfiguredSecret = expected.length === signature.length && timingSafeEqual(expected, signature)
    }
    result.issuerMatchesExpected = claims.iss === options.issuer
    result.audienceMatchesExpected = Array.isArray(claims.aud) ? claims.aud.includes(options.audience) : claims.aud === options.audience
    const now = (options.now ?? Date.now()) / 1000
    result.timeClaimsValid = Number.isFinite(claims.iat) && Number.isFinite(claims.exp)
      && claims.iat <= now + 30 && claims.exp > now - 30 && claims.exp > claims.iat
    result.roleIsOrdinaryUser = claims.role === 'USER'
  } catch {
    // Intentionally omit parser messages and every original value.
  }
  return result
}
