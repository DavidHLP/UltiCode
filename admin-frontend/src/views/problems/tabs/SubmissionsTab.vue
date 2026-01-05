<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { Badge } from '@/components/ui/badge'
import DataTable from '@/components/table/DataTable.vue'
import { CheckCircle2, XCircle, Clock, Loader2 } from 'lucide-vue-next'
import { problemsApi } from '@/api/admin/problems'
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

const getStatusVariant = (status: string) => {
  switch (status) {
    case 'ACCEPTED':
      return 'default'
    case 'PENDING':
      return 'secondary'
    case 'RUNNING':
      return 'secondary'
    default:
      return 'outline'
  }
}

const getStatusIcon = (status: string) => {
  switch (status) {
    case 'ACCEPTED':
      return CheckCircle2
    case 'PENDING':
      return Clock
    case 'RUNNING':
      return Loader2
    default:
      return XCircle
  }
}

const getStatusColor = (status: string) => {
  switch (status) {
    case 'ACCEPTED':
      return 'text-emerald-600 dark:text-emerald-400'
    case 'PENDING':
      return 'text-amber-600 dark:text-amber-400'
    case 'RUNNING':
      return 'text-blue-600 dark:text-blue-400'
    default:
      return 'text-muted-foreground'
  }
}

const columns: ColumnDef<Submission>[] = [
  {
    accessorKey: 'id',
    header: 'ID',
    cell: (info) => {
      const id = info.getValue() as string
      return h('span', { class: 'text-xs font-mono text-muted-foreground' }, id.slice(0, 8))
    },
  },
  {
    id: 'user',
    header: 'User',
    cell: (info) => {
      const user = info.row.original.user
      const displayName = user?.username || user?.name || 'Unknown'
      return h('div', { class: 'flex items-center gap-2' }, [
        h(
          'div',
          {
            class:
              'w-5 h-5 rounded-full bg-primary/10 flex items-center justify-center text-xs text-primary',
          },
          displayName.charAt(0).toUpperCase(),
        ),
        h('span', { class: 'text-sm' }, displayName),
      ])
    },
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: (info) => {
      const status = info.getValue() as string
      const Icon = getStatusIcon(status)
      return h('div', { class: 'flex items-center gap-1.5' }, [
        h(Icon, {
          class: ['w-3.5 h-3.5', getStatusColor(status), status === 'RUNNING' && 'animate-spin'],
        }),
        h(
          Badge,
          { variant: getStatusVariant(status), class: 'text-[10px] px-1.5 py-0' },
          () => status,
        ),
      ])
    },
  },
  {
    accessorKey: 'language',
    header: 'Language',
    cell: (info) => {
      const lang = info.getValue() as string
      return h('span', { class: 'text-xs font-mono px-1.5 py-0.5 rounded bg-muted' }, lang)
    },
  },
  {
    accessorKey: 'runtime',
    header: 'Runtime',
    cell: (info) => {
      const value = info.getValue() as number | null
      return h(
        'span',
        { class: 'text-sm text-muted-foreground tabular-nums' },
        value ? `${value}ms` : '-',
      )
    },
  },
  {
    accessorKey: 'memory',
    header: 'Memory',
    cell: (info) => {
      const value = info.getValue() as number | null
      return h(
        'span',
        { class: 'text-sm text-muted-foreground tabular-nums' },
        value ? `${value}KB` : '-',
      )
    },
  },
  {
    accessorKey: 'created_at',
    header: 'Submitted',
    cell: (info) => {
      const date = new Date(info.getValue() as string)
      return h('span', { class: 'text-sm text-muted-foreground' }, date.toLocaleString())
    },
  },
]

async function fetchSubmissions() {
  loading.value = true
  try {
    const response = await problemsApi.getProblemSubmissions(props.problemId, {
      page: pagination.value.pageIndex + 1,
      limit: pagination.value.pageSize,
    })
    submissions.value = (response as { data?: Submission[] }).data || []
    total.value = (response as { total?: number }).total || 0
  } catch (error) {
    console.error('Failed to fetch submissions:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSubmissions()
})

function updatePagination(newPagination: typeof pagination.value) {
  pagination.value = newPagination
  fetchSubmissions()
}
</script>

<template>
  <div
    v-if="submissions.length === 0 && !loading"
    class="flex flex-col items-center justify-center py-16 text-center border border-dashed rounded-lg"
  >
    <div class="w-10 h-10 rounded-full bg-muted flex items-center justify-center mb-3">
      <CheckCircle2 :size="20" class="text-muted-foreground" />
    </div>
    <p class="text-sm text-muted-foreground">No submissions found</p>
  </div>

  <DataTable
    v-else
    :columns="columns"
    :data="submissions"
    :pagination="pagination"
    :row-count="total"
    :loading="loading"
    empty-title="No submissions found"
    empty-description="This problem hasn't received any submissions yet."
    @update:pagination="updatePagination"
  />
</template>
