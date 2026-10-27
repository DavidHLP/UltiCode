#!/usr/bin/env node
// Prepare only generated disposable accounts through real Auth APIs.
import assert from 'node:assert/strict'
import { request } from 'playwright'

assert.equal(process.env.ACCEPTANCE_DISPOSABLE, 'true')
const origin = 'http://127.0.0.1:9002'
const admin = await request.newContext({ baseURL: origin })
const participant = await request.newContext({ baseURL: origin })
try {
  const login = await admin.post('/api/auth/login', { data: {
    username: process.env.ACCEPTANCE_MANAGEMENT_USERNAME,
    password: process.env.ACCEPTANCE_MANAGEMENT_PASSWORD,
  } })
  assert.equal(login.status(), 200, 'Disposable manager login failed')
  const meResponse = await admin.get('/api/auth/me')
  assert(meResponse.ok(), 'Manager identity read failed')
  const me = (await meResponse.json()).data
  assert.equal(me.user.role, 'SUPER_ADMIN', 'Fixture may grant only the generated superadmin')
  const csrf = (await admin.storageState()).cookies.find(cookie => cookie.name === 'csrf_token')
  assert(csrf?.value)
  const permissions = [
    ['READ', 'PROBLEM'], ['CREATE', 'PROBLEM'], ['UPDATE', 'PROBLEM'],
    ['READ', 'CONTEST'], ['CREATE', 'CONTEST'], ['UPDATE', 'CONTEST'], ['READ', 'SYSTEM'],
  ]
  for (const [action, resource] of permissions) {
    const response = await admin.post(`/api/auth/admin/users/${me.user.id}/permissions`, {
      headers: { 'X-CSRF-Token': decodeURIComponent(csrf.value) },
      data: { action, resource, expiresAt: new Date(Date.now() + 3_600_000).toISOString().slice(0, 19) },
    })
    assert(response.ok(), `Fixture permission ${action}:${resource} rejected`)
    assert.equal((await response.json()).code, 0)
  }
  const registered = await participant.post('/api/auth/register', { data: {
    username: process.env.ACCEPTANCE_USERNAME,
    password: process.env.ACCEPTANCE_PASSWORD,
    email: 'acceptance-user@localhost.test',
  } })
  assert(registered.ok(), 'Disposable ordinary participant registration failed')
  assert.equal((await registered.json()).code, 0)
  const currentParticipant = await participant.get('/api/auth/me')
  assert(currentParticipant.ok())
  assert.equal((await currentParticipant.json()).data.user.role, 'USER')
  console.log('Disposable ordinary participant and expiring direct manager permissions: PASS')
} finally {
  await admin.dispose()
  await participant.dispose()
}
