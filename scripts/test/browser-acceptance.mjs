#!/usr/bin/env node
// Real-service acceptance uses disposable fixtures, never routed responses or an in-memory Judge.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'

const consoleOrigin = localOrigin(process.env.ACCEPTANCE_CONSOLE_ORIGIN || 'http://127.0.0.1:9002')
const managementOrigin = localOrigin(process.env.ACCEPTANCE_MANAGEMENT_ORIGIN || 'http://127.0.0.1:9003')
const username = process.env.ACCEPTANCE_USERNAME
const password = process.env.ACCEPTANCE_PASSWORD
assert(username && password, 'Supply disposable ACCEPTANCE_USERNAME and ACCEPTANCE_PASSWORD')
assert.equal(process.env.ACCEPTANCE_DISPOSABLE, 'true', 'Explicit disposable-stack opt-in is required')
const output = resolve(process.env.ACCEPTANCE_OUTPUT || '.local/browser-acceptance')
await mkdir(output, { recursive: true })
const report = { startedAt: new Date().toISOString(), evidence: 'real-local-services', checks: [] }
const authObservations = []
const browser = await chromium.launch({
  headless: true,
  ...(process.env.ACCEPTANCE_CHROMIUM ? { executablePath: process.env.ACCEPTANCE_CHROMIUM } : {}),
})
const timer = setTimeout(() => { console.error('Acceptance exceeded five minutes'); browser.close().catch(() => {}) }, 300_000)
try {
  const context = await browser.newContext({ locale: 'en-US' })
  const page = await context.newPage()
  page.on('response', response => {
    const path = new URL(response.url()).pathname
    if (response.request().method() === 'POST' && /\/problems\/\d+\/submissions$/.test(path)) {
      report.submissionAttemptStatuses ||= []
      report.submissionAttemptStatuses.push(response.status())
    }
    if (path === '/api/auth/refresh') {
      report.authRefreshStatuses ||= []
      report.authRefreshStatuses.push(response.status())
    }
    if (response.status() !== 401 || authObservations.length >= 20) return
    authObservations.push((async () => {
      const request = response.request()
      const cookie = await request.headerValue('cookie') || ''
      const authorization = await request.headerValue('authorization') || ''
      const envelope = await response.json().catch(() => ({}))
      const code = envelope.code
      const safeCode = typeof code === 'number' && Number.isFinite(code) ? code
        : typeof code === 'string' && /^[A-Z0-9_:-]{1,80}$/i.test(code) ? code : null
      report.httpAuthFailures ||= []
      report.httpAuthFailures.push({ path: new URL(response.url()).pathname, method: request.method(),
        status: 401, code: safeCode, accessCookiePresent: /(?:^|;\s*)access_token=/.test(cookie),
        bearerPresent: authorization.startsWith('Bearer ') })
    })())
  })
  page.setDefaultTimeout(30_000)
  await login(page, consoleOrigin)
  await page.waitForURL(`${consoleOrigin}/problemset`)
  report.checks.push({ name: 'console-browser-login', passed: true })
  report.protectedSessionPreflight = []
  for (const path of ['/api/auth/me', '/api/submissions']) {
    const response = await page.request.get(`${consoleOrigin}${path}`)
    const envelope = await response.json().catch(() => ({}))
    report.protectedSessionPreflight.push({ path, status: response.status(),
      code: typeof envelope.code === 'number' ? envelope.code : null,
      accessCookiePresent: (await context.cookies(consoleOrigin)).some(cookie => cookie.name === 'access_token') })
    assert(response.ok(), `Protected session preflight ${path} HTTP ${response.status()}`)
  }
  report.checks.push({ name: 'auth-and-app-protected-session', passed: true })

  await page.goto(`${consoleOrigin}/problems/two-sum/description`)
  await page.getByRole('button', { name: 'Select programming language' }).click()
  await page.getByRole('menuitem', { name: 'Python', exact: true }).click()
  // Use Monaco's public text-model API: native EditContext typing may apply
  // automatic indentation. Submission still goes through the visible button
  // and the real services; verify its exact request body below.
  const code = 'class Solution:\n    def twoSum(self, nums, target):\n        seen = {}\n        for i, n in enumerate(nums):\n            if target - n in seen:\n                return [seen[target - n], i]\n            seen[n] = i\n        return []\n'
  await page.waitForFunction(() => window.__ULTICODE_MONACO__?.editor.getModels().length)
  await page.evaluate(value => window.__ULTICODE_MONACO__.editor.getModels()[0].setValue(value), code)
  report.checks.push({ name: 'problem-browser-editor', passed: true })

  const submitted = page.waitForResponse(response => response.request().method() === 'POST'
    && /\/problems\/\d+\/submissions$/.test(new URL(response.url()).pathname)
    && response.status() !== 401)
  await page.getByRole('button', { name: 'Submit Solution', exact: true }).click()
  const response = await submitted
  assert.equal(response.request().postDataJSON().code, code, 'Editor must submit the exact fixture')
  assert(response.ok(), `Submission HTTP ${response.status()}`)
  if (report.submissionAttemptStatuses?.includes(401)) {
    assert(report.authRefreshStatuses?.includes(200), 'A 401 retry requires the real successful Auth refresh flow')
  }
  const envelope = await response.json()
  assert(envelope.data?.id, 'Real submission response must contain its ID')
  const id = envelope.data.id
  report.submissionId = id
  report.checks.push({ name: 'browser-submission-intake', passed: true })
  let verdict
  for (let attempt = 0; attempt < 60; attempt++) {
    const readback = await page.request.get(`${consoleOrigin}/api/submissions/${encodeURIComponent(id)}`)
    assert(readback.ok(), `Submission readback HTTP ${readback.status()}`)
    verdict = (await readback.json()).data?.status
    if (verdict && !['pending', 'judging', 'queued', 'running'].includes(normalize(verdict))) break
    await page.waitForTimeout(2_000)
  }
  report.verdict = verdict
  assert.equal(normalize(verdict), 'accepted', `Expected real Judge Accepted, observed ${verdict}`)
  report.checks.push({ name: 'real-judge-accepted', passed: true })

  const management = await (await browser.newContext({ locale: 'en-US' })).newPage()
  await login(management, managementOrigin, process.env.ACCEPTANCE_MANAGEMENT_USERNAME || username, process.env.ACCEPTANCE_MANAGEMENT_PASSWORD || password)
  await management.waitForURL(url => !url.pathname.startsWith('/login'))
  report.checks.push({ name: 'management-browser-login', passed: true })
  for (const [path, api] of [['/problems', '/problems'], ['/contests', '/contest']]) {
    const loaded = management.waitForResponse(response => response.ok()
      && response.request().method() === 'GET'
      && new URL(response.url()).pathname.startsWith(`/api/admin${api}`))
    await management.goto(`${managementOrigin}${path}`)
    await loaded
    await management.getByRole('main').first().waitFor()
    report.checks.push({ name: `management${path}-real-read`, passed: true })
  }
  // Create an unpublished contest through the real wizard, then read it back.
  // Keep the fixture for diagnosis; never delete existing data as test cleanup.
  const contestSlug = `acceptance-${Date.now()}`
  report.contestSlug = contestSlug
  await management.getByRole('button', { name: 'Create Contest', exact: true }).click()
  const dialog = management.getByRole('dialog')
  await dialog.getByPlaceholder('Enter contest title', { exact: true }).fill(contestSlug)
  await dialog.getByPlaceholder('contest-slug', { exact: true }).fill(contestSlug)
  await dialog.getByRole('button', { name: 'Next', exact: true }).click()
  await dialog.getByRole('button', { name: 'Next', exact: true }).click()
  await dialog.locator('input[type="datetime-local"]').fill(new Date(Date.now() + 86_400_000).toISOString().slice(0, 16))
  await dialog.getByRole('button', { name: 'Next', exact: true }).click()
  await dialog.getByRole('button', { name: 'Add Problem', exact: true }).click()
  await management.getByRole('option').filter({ hasText: 'two-sum' }).click()
  await dialog.getByRole('button', { name: 'Next', exact: true }).click()
  const contestCreated = management.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/admin/contest')
  await dialog.getByRole('button', { name: 'Create Contest', exact: true }).click()
  const creation = await contestCreated
  assert(creation.ok(), `Contest creation HTTP ${creation.status()}`)
  const contest = (await creation.json()).data
  assert(contest?.id, 'Created contest must have a real ID')
  report.contestId = contest.id
  assert.equal(creation.request().postDataJSON().isPublished, false, 'Wizard must request an unpublished fixture')
  assert.equal(contest.isVisible, false, 'Acceptance fixture must remain invisible')
  const contestReadback = await management.request.get(`${managementOrigin}/api/admin/contest/${encodeURIComponent(contest.id)}`)
  assert(contestReadback.ok(), 'Contest readback must succeed')
  const persistedContest = (await contestReadback.json()).data
  assert.equal(persistedContest?.slug, contestSlug)
  assert.equal(persistedContest?.isVisible, false)
  report.checks.push({ name: 'management-contest-wizard-create-readback', passed: true })

  const contestsLoaded = page.waitForResponse(response => response.ok()
    && new URL(response.url()).pathname === '/api/contest/upcoming')
  await page.goto(`${consoleOrigin}/contest`)
  await contestsLoaded
  await page.getByRole('main').first().waitFor()
  report.checks.push({ name: 'contest-browser-entry', passed: true })
  report.passed = true
} catch (error) {
  report.passed = false
  // Never serialize browser storage, credentials, request bodies or source.
  let failure = String(error.message)
  for (const secret of [password, process.env.ACCEPTANCE_MANAGEMENT_PASSWORD]) {
    if (secret) failure = failure.replaceAll(secret, '[REDACTED]')
  }
  report.failure = failure.replaceAll(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED]').slice(0, 2000)
  process.exitCode = 1
} finally {
  await Promise.allSettled(authObservations)
  report.finishedAt = new Date().toISOString()
  await writeFile(resolve(output, 'report.json'), `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 })
  console.log(JSON.stringify(report))
  await browser.close()
  clearTimeout(timer)
}

function localOrigin(value) {
  const url = new URL(value)
  assert(['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname), 'Acceptance is loopback-only')
  assert(url.protocol === 'http:' && !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash,
    'Use a plain disposable HTTP origin')
  return url.origin
}
function normalize(value) { return String(value || '').toLowerCase().replaceAll(/[_\s-]/g, '') }
async function login(page, origin, loginUsername = username, loginPassword = password) {
  await page.goto(`${origin}/login`)
  await page.getByLabel('Username', { exact: true }).fill(loginUsername)
  await page.getByLabel('Password', { exact: true }).fill(loginPassword)
  await page.getByRole('button', { name: 'Login', exact: true }).click()
}
