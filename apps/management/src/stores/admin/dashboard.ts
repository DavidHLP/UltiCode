import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  dashboardApi,
  type DashboardStats,
  type ChartStatsResponse,
  type ChartQueryParams,
} from '@/api/admin/dashboard'
import { extractApiErrorMessage } from '@/utils/error'
import { createValueRequest } from '@/stores/createValueRequest'
export const useDashboardStore = defineStore('adminDashboard', () => {
  // Note: API returns unwrapped data (request.ts interceptor handles Result<T>)
  const stats = ref<DashboardStats | null>(null)
  const chartData = ref<ChartStatsResponse | null>(null)
  const statsRequest = createValueRequest({
    errorMessage: 'Failed to fetch dashboard stats',
    rethrow: true,
    onError: (err) => console.error('Failed to fetch dashboard stats:', err),
  })
  const loading = statsRequest.loading
  const error = statsRequest.error

  async function fetchStats() {
    const data = await statsRequest.run((signal) => dashboardApi.getStats(signal))
    if (data !== null) stats.value = data
    return data
  }

  async function fetchChartStats(params: ChartQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const data = await dashboardApi.getChartStats(params)
      chartData.value = data
      return data
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch chart data')
      console.error('Failed to fetch chart data:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    stats,
    chartData,
    loading,
    error,
    fetchStats,
    fetchChartStats,
    clearError,
  }
})
