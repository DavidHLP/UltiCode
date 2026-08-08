import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  dashboardApi,
  type DashboardStats,
  type ChartStatsResponse,
  type ChartQueryParams,
} from '@/api/admin/dashboard'
import { extractApiErrorMessage } from '@/utils/error'
export const useDashboardStore = defineStore('adminDashboard', () => {
  // Note: API returns unwrapped data (request.ts interceptor handles Result<T>)
  const stats = ref<DashboardStats | null>(null)
  const chartData = ref<ChartStatsResponse | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchStats() {
    loading.value = true
    error.value = null
    try {
      const data = await dashboardApi.getStats()
      stats.value = data
      return data
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch dashboard stats')
      console.error('Failed to fetch dashboard stats:', err)
      throw err
    } finally {
      loading.value = false
    }
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
