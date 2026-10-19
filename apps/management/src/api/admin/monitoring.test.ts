import { afterEach, expect, it, vi } from 'vitest'
import { apiGet } from '@/utils/request'
import { monitoringApi } from './monitoring'

vi.mock('@/utils/request', () => ({ apiGet: vi.fn() }))
afterEach(() => vi.restoreAllMocks())

it('keeps failed probes unavailable without inventing zero statistics or empty queues', async () => {
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.mocked(apiGet).mockRejectedValue(new Error('Network unavailable'))
  const stats = await monitoringApi.getAllStats()
  expect(Object.values(stats)).toEqual([null, null, null, null, null, null])
})

it('preserves independent successful responses including valid empty queues', async () => {
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.mocked(apiGet).mockImplementation(async (url) => {
    if (url === '/monitoring/queues') return []
    if (url === '/monitoring/database') return { activeConnections: 0, status: 'healthy' }
    throw new Error('Probe unavailable')
  })
  const stats = await monitoringApi.getAllStats()
  expect(stats.queues).toEqual([])
  expect(stats.database).toEqual({ activeConnections: 0, status: 'healthy' })
  expect(stats.resources).toBeNull()
  expect(stats.health).toBeNull()
})
