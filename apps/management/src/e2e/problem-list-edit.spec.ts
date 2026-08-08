import { test, expect } from '@playwright/test'

const ADMIN_BASE_URL = process.env.VITE_ADMIN_BASE_URL || 'http://localhost:9003'
const API_BASE_URL = process.env.VITE_API_BASE_URL || 'http://localhost:9003/api'

test.describe('Problem List Edit Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${ADMIN_BASE_URL}/login`)
  })

  test.describe('Create Flow', () => {
    test('should create a new problem list and redirect to edit page', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/new`)
      await page.waitForSelector('text=new_list')

      const nameInput = page.locator('input[placeholder*="name"]').first()
      await nameInput.fill('Test Problem List')

      const descriptionInput = page.locator('textarea').first()
      await descriptionInput.fill('This is a test description')

      await page.route(`${API_BASE_URL}/admin/problem-lists`, async (route) => {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'test-list-123',
            name: 'Test Problem List',
            description: 'This is a test description',
            isPublic: true,
            isFeatured: false,
            bannerOrder: 0,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        })
      })

      const createButton = page.locator('button:has-text("Create")').first()
      await createButton.click()

      await page.waitForURL(/\/problem-lists\/.*\/edit/)
      expect(page.url()).toMatch(/\/problem-lists\/.*\/edit/)
      await expect(page.locator('text=edit_list')).toBeVisible()
    })

    test('should show validation error when name is empty', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/new`)
      await page.waitForSelector('text=new_list')

      const createButton = page.locator('button:has-text("Create")').first()
      await createButton.click()

      await expect(page.locator('text=Name is required')).toBeVisible()
    })
  })

  test.describe('Edit Flow with Auto-Save', () => {
    test('should auto-save name field after debounce', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/test-list-123/edit`)

      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'test-list-123',
            name: 'Original Name',
            description: 'Original description',
            isPublic: true,
            isFeatured: false,
            bannerTag: '',
            bannerTheme: 'blue',
            bannerOrder: 0,
            problems: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        })
      })

      let patchRequestMade = false
      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        if (route.request().method() === 'PATCH') {
          patchRequestMade = true
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true }),
          })
        } else {
          await route.continue()
        }
      })

      await page.waitForSelector('text=edit_list')

      const nameInput = page.locator('input[placeholder*="name"]').first()
      await nameInput.fill('Updated Name')

      await page.waitForTimeout(1500)
      expect(patchRequestMade).toBe(true)
      await expect(page.locator('text=saved')).toBeVisible()
    })

    test('should save on blur immediately', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/test-list-123/edit`)

      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'test-list-123',
            name: 'Original Name',
            description: 'Original description',
            isPublic: true,
            isFeatured: false,
            bannerTag: '',
            bannerTheme: 'blue',
            bannerOrder: 0,
            problems: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        })
      })

      let patchRequestMade = false
      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        if (route.request().method() === 'PATCH') {
          patchRequestMade = true
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true }),
          })
        } else {
          await route.continue()
        }
      })

      await page.waitForSelector('text=edit_list')

      const nameInput = page.locator('input[placeholder*="name"]').first()
      await nameInput.fill('Blur Test Name')
      await nameInput.blur()

      await page.waitForTimeout(500)
      expect(patchRequestMade).toBe(true)
    })
  })

  test.describe('Module Sections Visibility', () => {
    test('should display all three sections on edit page', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/test-list-123/edit`)

      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'test-list-123',
            name: 'Test List',
            description: 'Test description',
            isPublic: true,
            isFeatured: false,
            bannerTag: 'test-tag',
            bannerTheme: 'blue',
            bannerOrder: 1,
            problems: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        })
      })

      await page.waitForSelector('text=edit_list')

      await expect(page.locator('text=Basic Info').first()).toBeVisible()
      await expect(page.locator('text=Visibility').first()).toBeVisible()
      await expect(page.locator('text=Banner').first()).toBeVisible()
      await expect(page.locator('text=Problems').first()).toBeVisible()
    })

    test('should show create button in create mode', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/new`)
      await page.waitForSelector('text=new_list')

      await expect(page.locator('button:has-text("Create")').first()).toBeVisible()
      await expect(page.locator('text=saved')).not.toBeVisible()
    })
  })

  test.describe('Error Handling', () => {
    test('should handle network error during auto-save', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/test-list-123/edit`)

      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'test-list-123',
            name: 'Original Name',
            description: 'Original description',
            isPublic: true,
            isFeatured: false,
            bannerTag: '',
            bannerTheme: 'blue',
            bannerOrder: 0,
            problems: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        })
      })

      await page.route(`${API_BASE_URL}/admin/problem-lists/test-list-123`, async (route) => {
        if (route.request().method() === 'PATCH') {
          await route.fulfill({
            status: 500,
            contentType: 'application/json',
            body: JSON.stringify({ message: 'Internal server error' }),
          })
        } else {
          await route.continue()
        }
      })

      await page.waitForSelector('text=edit_list')

      const nameInput = page.locator('input[placeholder*="name"]').first()
      await nameInput.fill('Error Test Name')
      await nameInput.blur()

      await page.waitForTimeout(1000)
      await expect(page.locator('text=error').first()).toBeVisible()
    })

    test('should handle 409 conflict on create', async ({ page }) => {
      await page.goto(`${ADMIN_BASE_URL}/problem-lists/new`)
      await page.waitForSelector('text=new_list')

      await page.route(`${API_BASE_URL}/admin/problem-lists`, async (route) => {
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'A list with this name already exists' }),
        })
      })

      const nameInput = page.locator('input[placeholder*="name"]').first()
      await nameInput.fill('Duplicate Name')

      const createButton = page.locator('button:has-text("Create")').first()
      await createButton.click()

      await expect(page.locator('text=A list with this name already exists')).toBeVisible()
    })
  })
})
