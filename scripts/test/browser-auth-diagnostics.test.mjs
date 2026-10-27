import test from 'node:test'
import assert from 'node:assert/strict'
import { createHmac } from 'node:crypto'
import { summarizeAccessCookie } from './browser-auth-diagnostics.mjs'

const secret = 'fixture-private-canary-secret-at-least-32-characters'
const claims = { sub: 'fixture-private-actor', role: 'USER', iss: 'fixture-issuer', aud: 'fixture-api', iat: 1000, exp: 2000 }
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
const data = encode({ alg: 'HS256' }) + '.' + encode(claims)
const token = data + '.' + createHmac('sha256', secret).update(data).digest('base64url')
const cookie = 'access_token=' + token + '; csrf_token=private-csrf-canary'
const options = { secret, preflightToken: token, expectedActorId: claims.sub, issuer: claims.iss,
  audience: claims.aud, now: 1500000, csrfHeader: 'private-csrf-canary' }

test('diagnostic results contain only booleans, never credentials or claims', () => {
  const result = summarizeAccessCookie(cookie, options)
  assert(Object.values(result).every(value => value === true))
  const encoded = JSON.stringify(result)
  for (const value of [secret, token, claims.sub, claims.iss, claims.aud, options.csrfHeader]) assert(!encoded.includes(value))
})

test('wrong signature, expiry, actor or CSRF are reported without authenticating or exposing data', () => {
  const result = summarizeAccessCookie(cookie, { ...options, secret: 'another-private-secret-at-least-32-characters',
    now: 3000000, expectedActorId: 'other-actor', csrfHeader: 'stale-token' })
  assert.equal(result.hmacMatchesConfiguredSecret, false)
  assert.equal(result.timeClaimsValid, false)
  assert.equal(result.subjectMatchesProtectedActor, false)
  assert.equal(result.csrfHeaderMatchesCookie, false)
  assert(Object.values(result).every(value => typeof value === 'boolean'))
  assert.equal(summarizeAccessCookie('access_token=malformed-private-canary', options).parsed, false)
})
