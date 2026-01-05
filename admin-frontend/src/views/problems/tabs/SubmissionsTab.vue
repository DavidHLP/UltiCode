<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import DataTable from '@/components/table/DataTable.vue'
import { Badge } from '@/components/ui/badge'
import type { ColumnDef } from '@tanstack/vue-table'

const props = defineProps<{
  problemId: string
}>()

interface Submission {
  id: string
  problem_id: string
  user_id: string
  status: string
  language: string
  runtime: number | null
  memory: number | null
  created_at: string
  user?: {
    id: string
    username: string
    name: string | null
  }
}

const submissions = ref<Submission[]>([])
const loading = ref(false)
const pagination = ref({ pageIndex: 0, pageSize: 20 })
const total = ref(0)

const columns: ColumnDef<Submission, string | number>[] = [
  {
    accessorKey: 'id',
    header: 'ID',
    cell: (info) => info.getValue(),
  },
  {
    id: 'user',
    header: 'User',
    cell: (info) => info.row.original.user?.username || info.row.original.user?.name || 'Unknown',
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: (info) => {
      const status = info.getValue() as string
      const variant = status === 'ACCEPTED' ? 'default' : 'destructive'
      return h(Badge, { variant }, () => status)
    },
  },
  {
    accessorKey: 'language',
    header: 'Language',
    cell: (info) => info.getValue(),
  },
  {
    accessorKey: 'runtime',
    header: 'Runtime',
    cell: (info) => (info.getValue() ? `${info.getValue()}ms` : '-'),
  },
  {
    accessorKey: 'memory',
    header: 'Memory',
    cell: (info) => (info.getValue() ? `${info.getValue()}KB` : '-'),
  },
  {
    accessorKey: 'created_at',
    header: 'Submitted',
    cell: (info) => new Date(info.getValue() as string).toLocaleString(),
  },
]

async function fetchSubmissions() {
  loading.value = true
  try {
    const response = await fetch(
      `/api/admin/problems/${props.problemId}/submissions?page=${pagination.value.pageIndex + 1}&limit=${pagination.value.pageSize}`,
    )
    const data = await response.json()
    submissions.value = data.data || []
    total.value = data.total || 0
  } catch (error) {
    console.error('Failed to fetch submissions:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSubmissions()
})

// Refetch when pagination changes
function updatePagination(newPagination: typeof pagination.value) {
  pagination.value = newPagination
  fetchSubmissions()
}
</script>

<template>
  <div class="p-4">
    <h3 class="text-lg font-semibold mb-4">Submissions</h3>
    <DataTable
      :columns="columns"
      :data="submissions"
      :pagination="pagination"
      :row-count="total"
      :loading="loading"
      empty-title="No submissions found"
      empty-description="This problem hasn't received any submissions yet."
      @update:pagination="updatePagination"
    />
  </div>
</template>
