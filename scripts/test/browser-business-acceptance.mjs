#!/usr/bin/env node
// Additional real-business checks on existing disposable accounts and contest.
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { chromium } from 'playwright'

assert.equal(process.env.ACCEPTANCE_DISPOSABLE, 'true')
const user = process.env.ACCEPTANCE_USERNAME
const password = process.env.ACCEPTANCE_PASSWORD
const manager = process.env.ACCEPTANCE_MANAGEMENT_USERNAME
const managerPassword = process.env.ACCEPTANCE_MANAGEMENT_PASSWORD
assert(user && password && manager && managerPassword, 'Existing disposable credentials are required')
const consoleOrigin = 'http://127.0.0.1:9002'
const managementOrigin = 'http://127.0.0.1:9003'
const prior = JSON.parse(await readFile('.local/browser-acceptance/report.json', 'utf8'))
assert(prior.passed && prior.submissionId && prior.contestId, 'Run the base acceptance first')
const selected = process.env.ACCEPTANCE_CHECKS ? new Set(process.env.ACCEPTANCE_CHECKS.split(',')) : null
const output = '.local/browser-business-acceptance'
await mkdir(output, { recursive: true })
const report = { startedAt: new Date().toISOString(), evidence: 'real-disposable-services', checks: [], fixtures: { submissionId: prior.submissionId, contestId: prior.contestId, contestSlug: prior.contestSlug } }
const browser = await chromium.launch({ headless: true, ...(process.env.ACCEPTANCE_CHROMIUM ? { executablePath: process.env.ACCEPTANCE_CHROMIUM } : {}) })
const timer = setTimeout(() => browser.close().catch(() => {}), 600_000)
try {
  const participant = await (await browser.newContext({ locale: 'en-US' })).newPage()
  const admin = await (await browser.newContext({ locale: 'en-US' })).newPage()
  await login(participant, consoleOrigin, user, password)
  await login(admin, managementOrigin, manager, managerPassword)

  await check('real-wrong-answer-terminal', async () => {
    await openEditor(participant, `${consoleOrigin}/problems/two-sum/description`)
    const id = await submit(participant, 'class Solution:\n    def twoSum(self, nums, target):\n        return []\n')
    report.fixtures.wrongAnswerSubmissionId = id
    await save()
    report.wrongAnswerStates = await terminal(participant, id, 'wronganswer')
  })

  await check('management-problem-create-edit-readback', async () => {
    const slug = `acceptance-problem-${Date.now()}`
    report.fixtures.problemSlug = slug
    await admin.goto(`${managementOrigin}/problems/create`)
    await admin.getByPlaceholder('Enter problem title', { exact: true }).fill(slug)
    await admin.getByPlaceholder('two-sum', { exact: true }).fill(slug)
    await admin.getByPlaceholder('Enter problem content...', { exact: true }).fill('Return the supplied integer. Synthetic acceptance fixture.')
    await admin.getByPlaceholder('Enter problem summary...', { exact: true }).fill('Synthetic summary')
    await admin.getByPlaceholder('Add a hint...', { exact: true }).fill('Return n')
    await admin.getByPlaceholder('Add a hint...', { exact: true }).press('Enter')
    await admin.getByPlaceholder('Add language...', { exact: true }).fill('python')
    await admin.getByPlaceholder('Add language...', { exact: true }).press('Enter')
    await admin.getByPlaceholder('Enter test input...', { exact: true }).fill('1')
    await admin.getByPlaceholder('Enter expected output...', { exact: true }).fill('1')
    const constraint = admin.getByPlaceholder('e.g., 1 <= nums.length <= 10^4', { exact: true })
    await constraint.fill('1 <= n <= 10')
    await constraint.press('Enter')
    const created = admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/admin/problems')
    await admin.getByRole('button', { name: 'Create Problem', exact: true }).click()
    const response = await created
    const envelope = await response.json()
    assert(response.ok(), `Problem create HTTP ${response.status()}: ${envelope.message}`)
    const data = envelope.data
    assert(data?.id, 'Created problem must have an ID')
    report.fixtures.problemId = data.id
    await save()
    assert.equal((await api(admin, `/admin/problems/${data.id}`)).title, slug)
    const description = await api(admin, `/admin/problems/${data.id}/description`)
    assert.equal(description.difficulty.toLowerCase(), 'medium')
    assert.equal(description.isPublished, false)
    assert.equal(description.detail.summary, 'Synthetic summary')
    assert.equal(description.detail.content, 'Return the supplied integer. Synthetic acceptance fixture.')
    assert.deepEqual(description.detail.constraintsJson, ['1 <= n <= 10'])
    assert.deepEqual(description.detail.hints, ['Return n'])
    assert.equal(description.examples[0].input, '1')
    assert.equal(description.examples[0].output, '1')
    assert((await api(admin, `/admin/problems/${data.id}/code`)).languages.some(language => language.value === 'python'))
    await admin.goto(`${managementOrigin}/problems/${data.id}/edit/description`)
    await admin.getByPlaceholder('Enter problem title', { exact: true }).fill(`${slug}-edited`)
    const updated = admin.waitForResponse(r => r.request().method() === 'PATCH' && new URL(r.url()).pathname === `/api/admin/problems/${data.id}`)
    await admin.getByRole('button', { name: 'Update Description', exact: true }).click()
    assert((await updated).ok(), 'Problem update must succeed')
    const readback = await api(admin, `/admin/problems/${data.id}`)
    assert.equal(readback.title, `${slug}-edited`)
    assert.equal(readback.slug, slug)
    // A real partial update must retain the title and persist every supplied owner section.
    await api(admin, `/admin/problems/${data.id}`, 'PATCH', {
      difficulty: 'Hard', isPublished: true, summary: 'Edited summary', content: 'Edited synthetic content.',
      constraintsJson: '["2 <= n <= 20"]', hints: '["Edited hint"]',
      examples: '[{"input":"2","output":"2","explanation":"identity"}]',
      languages: [{ language: 'python', starterCode: 'class Solution:\n    pass\n' }], tags: [],
    })
    const edited = await api(admin, `/admin/problems/${data.id}/description`)
    assert.equal(edited.title, `${slug}-edited`)
    assert.equal(edited.difficulty.toLowerCase(), 'hard')
    assert.equal(edited.isPublished, true)
    assert.equal(edited.detail.summary, 'Edited summary')
    assert.equal(edited.detail.content, 'Edited synthetic content.')
    assert.deepEqual(edited.detail.constraintsJson, ['2 <= n <= 20'])
    assert.deepEqual(edited.detail.hints, ['Edited hint'])
    assert.equal(edited.examples[0].input, '2')
    assert.equal(edited.examples[0].output, '2')
    const code = await api(admin, `/admin/problems/${data.id}/code`)
    assert.equal(code.languages.find(language => language.value === 'python').starterCode, 'class Solution:\n    pass\n')
    const invalidSlug = `${slug}-invalid`;
    await rejectedWrite(admin, '/admin/problems', 'POST', {
      slug: invalidSlug, title: invalidSlug, difficulty: 'Easy', content: 'Must roll back', languages: ['unsupported-language'],
    })
    assert.equal((await api(admin, `/admin/problems?search=${invalidSlug}`)).total, 0)
    await rejectedWrite(admin, `/admin/problems/${data.id}`, 'PATCH', {
      title: 'Must not persist', content: 'Must roll back', languages: [{ language: 'unsupported-language', starterCode: 'invalid' }],
    })
    const afterRejected = await api(admin, `/admin/problems/${data.id}/description`)
    assert.equal(afterRejected.title, `${slug}-edited`)
    assert.equal(afterRejected.detail.content, 'Edited synthetic content.')
    assert((await api(admin, `/admin/problems/${data.id}/code`)).languages.some(language => language.value === 'python'))
    report.problemInvalidChildRollback = true
    await participant.goto(`${consoleOrigin}/problems/${slug}/description`)
    await participant.getByText('Edited synthetic content.', { exact: true }).waitFor()
    report.problemPersistedReadback = { id: data.id, difficulty: edited.difficulty, published: edited.isPublished, sections: ['title', 'summary', 'content', 'constraints', 'hints', 'examples', 'languages', 'tags'], consoleVisible: true }
  })

  await check('management-rejudge-progression-terminal', async () => {
    const id = prior.submissionId
    await admin.goto(`${managementOrigin}/submissions`)
    await admin.getByPlaceholder('Search submissions...', { exact: true }).fill(id)
    const row = admin.locator('tr').filter({ hasText: id.slice(0, 8) })
    await row.getByRole('button', { name: 'Rejudge', exact: true }).click()
    const receipt = admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === `/api/admin/submissions/${id}/rejudge`)
    await admin.getByRole('dialog').getByRole('button', { name: 'Rejudge', exact: true }).click()
    const response = await receipt
    assert(response.ok(), `Rejudge HTTP ${response.status()}`)
    const data = (await response.json()).data
    assert.equal(data?.success, true)
    assert.equal(normalize(data.newStatus), 'pending')
    assert(Number(data.retryCount) >= 1)
    report.rejudgeReceipt = { oldStatus: data.oldStatus, newStatus: data.newStatus, retryCount: data.retryCount }
    await save()
    report.rejudgeStates = await terminal(participant, id, 'accepted')
  })

  await check('contest-participant-submission-ranking-lifecycle', async () => {
    const id = prior.contestId
    const slug = prior.contestSlug
    let contest = await api(admin, `/admin/contest/${id}`)
    if (!contest.isVisible) contest = await api(admin, `/admin/contest/${id}`, 'PATCH', { isPublished: true })
    assert.equal(contest.isVisible, true)
    await participant.goto(`${consoleOrigin}/contest/${slug}`)
    const participation = await api(participant, `/contest/${slug}/participation`)
    if (normalize(participation.status) === 'notparticipated') {
      const registered = participant.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/register'))
      await participant.getByRole('button', { name: 'Register', exact: true }).first().click()
      assert((await registered).ok(), 'Participant registration must succeed')
    }
    report.contestRegistered = true
    await save()
    if (contest.status === 'UPCOMING') contest = await api(admin, `/admin/contest/${id}/start`, 'POST')
    assert.equal(contest.status, 'RUNNING')
    report.contestStarted = true
    await save()
    await participant.goto(`${consoleOrigin}/contest/${slug}`)
    await participant.locator('#contest-problems a[href*="contestId="]').first().click()
    await participant.getByRole('button', { name: 'Select programming language' }).click()
    await participant.getByRole('menuitem', { name: 'Python', exact: true }).click()
    const submissionId = await submit(participant, 'class Solution:\n    def twoSum(self, nums, target):\n        seen = {}\n        for i, n in enumerate(nums):\n            if target-n in seen: return [seen[target-n], i]\n            seen[n] = i\n        return []\n', true)
    report.fixtures.contestSubmissionId = submissionId
    await save()
    report.contestSubmissionStates = await terminal(participant, submissionId, 'accepted')
    let ranking
    for (let attempt = 0; attempt < 60; attempt++) {
      const data = await api(participant, `/contest/${slug}/live-ranking`)
      const entries = Array.isArray(data) ? data : data.items || data.records || []
      ranking = entries.find(entry => entry.username === user)
      if (ranking && Number(ranking.problemsSolved) >= 1) break
      await participant.waitForTimeout(1000)
    }
    assert(ranking, 'Participant must appear in real ranking')
    report.ranking = { rank: ranking.rank, solved: ranking.problemsSolved, score: ranking.score }
    assert(Number(report.ranking.solved) >= 1)
    const ended = await api(admin, `/admin/contest/${id}/end`, 'POST')
    assert(['FINISHING', 'FINISHED'].includes(ended.status))
    let final = ended
    for (let attempt = 0; attempt < 60 && final.status !== 'FINISHED'; attempt++) {
      await participant.waitForTimeout(1000)
      final = await api(admin, `/admin/contest/${id}`)
    }
    assert.equal(final.status, 'FINISHED')
    const snapshot = await api(participant, `/contest/${slug}/ranking`)
    const entries = snapshot.items || []
    const finalRank = entries.find(entry => entry.username === user)
    assert(finalRank && Number(finalRank.problemsSolved) >= 1, 'Final ranking must retain the solved problem')
    report.finalRanking = { rank: finalRank.rank, problemsSolved: finalRank.problemsSolved, score: finalRank.score }
    report.contestFinalStatus = final.status
  })
} finally {
  report.passed = report.checks.length === (selected?.size || 4) && report.checks.every(check => check.passed)
  report.finishedAt = new Date().toISOString()
  await save()
  console.log(JSON.stringify(report))
  process.exitCode = report.passed ? 0 : 1
  await browser.close()
  clearTimeout(timer)
}

async function save() { await writeFile(`${output}/report.json`, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 }) }
async function check(name, action) {
  if (selected && !selected.has(name)) return
  try { await action(); report.checks.push({ name, passed: true }) }
  catch (error) {
    let message = String(error.message)
    for (const secret of [password, managerPassword]) message = message.replaceAll(secret, '[REDACTED]')
    message = message.replaceAll(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED]')
    report.checks.push({ name, passed: false, failure: message.slice(0, 1600) })
  }
  await save()
}
async function login(page, origin, username, secret) {
  await page.goto(`${origin}/login`)
  await page.getByLabel('Username', { exact: true }).fill(username)
  await page.getByLabel('Password', { exact: true }).fill(secret)
  await page.getByRole('button', { name: 'Login', exact: true }).click()
  await page.waitForURL(url => !url.pathname.startsWith('/login'))
}
async function api(page, path, method = 'GET', data) {
  const origin = page === undefined ? consoleOrigin : new URL(page.url()).origin
  const csrf = (await page.context().cookies()).find(cookie => cookie.name === 'csrf_token')?.value
  const response = await page.request.fetch(`${origin}/api${path}`, { method, timeout: 15000, ...(data ? { data } : {}), ...(method !== 'GET' && csrf ? { headers: { 'X-CSRF-Token': decodeURIComponent(csrf) } } : {}) })
  assert(response.ok(), `${method} ${path} HTTP ${response.status()}`)
  const value = await response.json()
  assert.equal(value.code, 0, `${method} ${path} rejected: ${value.message}`)
  return value.data
}
async function rejectedWrite(page, path, method, data) {
  const csrf = (await page.context().cookies()).find(cookie => cookie.name === 'csrf_token')?.value
  const response = await page.request.fetch(`${managementOrigin}/api${path}`, { method, data,
    headers: { 'X-CSRF-Token': decodeURIComponent(csrf) }, timeout: 15000 })
  assert.equal(response.status(), 400, 'Invalid child section must be rejected as bad input')
  assert.notEqual((await response.json()).code, 0)
}
async function openEditor(page, url) {
  await page.goto(url)
  await page.getByRole('button', { name: 'Select programming language' }).click()
  await page.getByRole('menuitem', { name: 'Python', exact: true }).click()
}
async function submit(page, code, contest = false) {
  await page.waitForFunction(() => window.__ULTICODE_MONACO__?.editor.getModels().length)
  await page.evaluate(value => window.__ULTICODE_MONACO__.editor.getModels()[0].setValue(value), code)
  const receipt = page.waitForResponse(r => r.request().method() === 'POST' && (contest ? /\/contest\/[^/]+\/problems\/\d+\/submissions$/ : /\/problems\/\d+\/submissions$/).test(new URL(r.url()).pathname))
  await page.getByRole('button', { name: 'Submit Solution', exact: true }).click()
  const response = await receipt
  assert.equal(response.request().postDataJSON().code, code)
  assert(response.ok(), `Submission HTTP ${response.status()}`)
  const value = await response.json()
  assert(value.data?.id, `Submission rejected: ${value.message}`)
  return value.data.id
}
async function terminal(page, id, expected) {
  const states = []
  for (let attempt = 0; attempt < 90; attempt++) {
    const data = await api(page, `/submissions/${id}`)
    if (states.at(-1) !== data.status) states.push(data.status)
    if (!['pending', 'judging', 'queued', 'running'].includes(normalize(data.status))) {
      assert.equal(normalize(data.status), expected, `Unexpected terminal verdict: ${data.status}`)
      return states
    }
    await page.waitForTimeout(1000)
  }
  throw Error(`No terminal verdict after 90 polls; states=${states.join(',')}`)
}
function normalize(value) { return String(value || '').toLowerCase().replaceAll(/[_\s-]/g, '') }
