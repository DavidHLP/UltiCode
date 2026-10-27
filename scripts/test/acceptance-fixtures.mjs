#!/usr/bin/env node
// Prepare only generated disposable accounts through real Auth APIs.
import assert from 'node:assert/strict'
import { request } from 'playwright'

assert.equal(process.env.ACCEPTANCE_DISPOSABLE, 'true')
const origin = 'http://127.0.0.1:9002'
const admin = await request.newContext({ baseURL: 'http://127.0.0.1:9003' })
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
  let csrf = (await admin.storageState()).cookies.find(cookie => cookie.name === 'csrf_token')
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
  // Permission writes advance authorization state. Obtain a real fresh session
  // before crossing into Management, and use the rotated double-submit token.
  csrf = (await admin.storageState()).cookies.find(cookie => cookie.name === 'csrf_token')
  assert(csrf?.value)
  const refreshed = await admin.post('/api/auth/refresh', {
    headers: { 'X-CSRF-Token': decodeURIComponent(csrf.value) },
  })
  const refreshBody = await refreshed.json().catch(() => ({}))
  console.log(JSON.stringify({ fixtureStep: 'manager-session-refresh', status: refreshed.status(),
    code: typeof refreshBody.code === 'number' ? refreshBody.code : null }))
  assert(refreshed.ok() && refreshBody.code === 0, 'Manager session refresh after permission writes failed')
  csrf = (await admin.storageState()).cookies.find(cookie => cookie.name === 'csrf_token')
  assert(csrf?.value)
  const refreshedMe = await admin.get('/api/auth/me')
  assert(refreshedMe.ok(), 'Refreshed manager identity read failed')
  const refreshedIdentity = (await refreshedMe.json()).data
  assert.equal(refreshedIdentity.user.id, me.user.id, 'Refresh must preserve the fixture actor')
  assert.equal(refreshedIdentity.user.role, 'SUPER_ADMIN')
  // The canonical DEV-LOCAL seed supplies the published problem, languages and
  // tags. Judge cases are separate owner data: prepare them explicitly through
  // the real Management API, with server-generated IDs and structured inputs.
  const descriptionResponse = await admin.get('/api/admin/problems/1/description')
  const descriptionBody = await descriptionResponse.json().catch(() => ({}))
  const descriptionCode = typeof descriptionBody.code === 'number' ? descriptionBody.code : null
  console.log(JSON.stringify({ fixtureStep: 'manager-seed-description', status: descriptionResponse.status(), code: descriptionCode }))
  assert(descriptionResponse.ok() && descriptionCode === 0,
    `Manager seed description HTTP ${descriptionResponse.status()}, code ${descriptionCode}`)
  const description = descriptionBody.data
  assert.equal(description.slug, 'two-sum')
  assert.equal(description.isPublished, true)
  assert(description.tags.length > 0, 'CRUD tag roundtrip requires seeded tags')
  const codeResponse = await admin.get('/api/admin/problems/1/code')
  assert(codeResponse.ok())
  assert((await codeResponse.json()).data.languages.some(language => language.value === 'python'))
  for (const [nums, target, expected] of [[[3, 3], 6, [0, 1]], [[2, 5, 5, 11], 10, [1, 2]]]) {
    const inputs = JSON.stringify([{ name: 'nums', value: nums }, { name: 'target', value: target }])
    const created = await admin.post('/api/admin/problems/1/test-cases', {
      headers: { 'X-CSRF-Token': decodeURIComponent(csrf.value) },
      data: { isSample: false, isHidden: true, inputText: `nums = ${JSON.stringify(nums)}, target = ${target}`,
        outputText: JSON.stringify(expected), inputs },
    })
    assert(created.ok(), 'Disposable hidden Judge fixture creation failed')
    const envelope = await created.json()
    assert.equal(envelope.code, 0)
    assert(envelope.data?.id, 'Judge fixture must have a server-generated ID')
    const readback = await admin.get(`/api/admin/problems/1/test-cases/${envelope.data.id}`)
    assert(readback.ok())
    const persisted = (await readback.json()).data
    assert.equal(persisted.isHidden, true)
    assert.equal(persisted.isSample, false)
    assert.equal(persisted.outputText, JSON.stringify(expected))
    assert.deepEqual(JSON.parse(persisted.inputs), JSON.parse(inputs))
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
  console.log('Canonical problem, persisted hidden Judge cases, ordinary participant and expiring manager permissions: PASS')
} finally {
  await admin.dispose()
  await participant.dispose()
}
