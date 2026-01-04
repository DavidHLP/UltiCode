<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuditStore } from '@/stores/admin/audit'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const auditStore = useAuditStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const actionFilter = ref<string>('all')
const entityTypeFilter = ref<string>('all')
const page = ref(1)
const pageSize = ref(50)

const canExportLogs = computed(() => authStore.hasPermission('READ', 'SYSTEM'))

onMounted(() => loadLogs())

async function loadLogs() {
  await auditStore.fetchLogs({
    action: actionFilter.value === 'all' ? undefined : actionFilter.value,
    entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
    page: page.value,
    limit: pageSize.value,
  })
}

async function exportLogs() {
  try {
    await auditStore.exportLogs({
      action: actionFilter.value === 'all' ? undefined : actionFilter.value,
      entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
      format: 'csv',
    })
    alert('Export complete')
  } catch {
    alert('Failed to export logs')
  }
}

function formatDate(date: Date | string): string {
  const d = new Date(date)
  return d.toLocaleString()
}

function getActionBadgeVariant(action: string) {
  const actionUpper = action.toUpperCase()
  if (actionUpper.includes('CREATE') || actionUpper.includes('GRANT')) {
    return 'default'
  }
  if (actionUpper.includes('UPDATE') || actionUpper.includes('PUBLISH')) {
    return 'secondary'
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'destructive'
  }
  return 'outline'
}

function formatJson(value: unknown): string {
  if (!value) return '-'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

interface AuditLog {
  id: string
  action: string
  entity_type?: string
  entity_id?: string
  performer?: { username: string; role: string }
  user?: { username: string }
  created_at: string | Date
  old_values?: unknown
  new_values?: unknown
}

function showDetails(log: AuditLog) {
  const details = `
Action: ${log.action}
Entity: ${log.entity_type || 'N/A'} (${log.entity_id || 'N/A'})
Performer: ${log.performer?.username || 'N/A'} (${log.performer?.role || 'N/A'})
Target User: ${log.user?.username || 'N/A'}
Date: ${formatDate(log.created_at)}

Old Values:
${formatJson(log.old_values)}

New Values:
${formatJson(log.new_values)}
  `
  alert(details)
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Audit Logs</h1>
        <p class="text-muted-foreground">View all admin actions and system events</p>
      </div>
      <Button v-if="canExportLogs" variant="outline" @click="exportLogs"> Export CSV </Button>
    </div>

    <Card>
      <CardHeader>
        <div class="flex items-center gap-4">
          <Input
            v-model="searchQuery"
            placeholder="Search logs..."
            class="max-w-sm"
            @keyup.enter="loadLogs()"
          />
          <Select v-model="actionFilter" @update:model-value="loadLogs()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by action" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Actions</SelectItem>
              <SelectItem value="CREATE_USER">Create User</SelectItem>
              <SelectItem value="UPDATE_USER">Update User</SelectItem>
              <SelectItem value="DELETE_USER">Delete User</SelectItem>
              <SelectItem value="BAN_USER">Ban User</SelectItem>
              <SelectItem value="UNBAN_USER">Unban User</SelectItem>
              <SelectItem value="GRANT_PERMISSION">Grant Permission</SelectItem>
              <SelectItem value="REVOKE_PERMISSION">Revoke Permission</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="entityTypeFilter" @update:model-value="loadLogs()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by entity" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Entities</SelectItem>
              <SelectItem value="USER">User</SelectItem>
              <SelectItem value="PROBLEM">Problem</SelectItem>
              <SelectItem value="CONTEST">Contest</SelectItem>
              <SelectItem value="SOLUTION">Solution</SelectItem>
              <SelectItem value="FORUM_POST">Forum Post</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        <div v-if="auditStore.loading" class="text-center py-8">Loading...</div>

        <div v-else-if="auditStore.error" class="text-center py-8 text-red-600">
          {{ auditStore.error }}
        </div>

        <div v-else class="space-y-2">
          <div
            v-for="log in auditStore.logs"
            :key="log.id"
            class="flex items-center gap-4 p-4 border rounded-lg hover:bg-muted/50 text-sm"
          >
            <div class="min-w-[140px] text-muted-foreground">
              {{ formatDate(log.created_at) }}
            </div>

            <Badge :variant="getActionBadgeVariant(log.action)">
              {{ log.action }}
            </Badge>

            <div v-if="log.entity_type" class="text-muted-foreground">
              {{ log.entity_type }}
            </div>

            <div class="flex-1">
              <span class="font-medium">{{ log.performer?.username || 'System' }}</span>
              <span v-if="log.user" class="text-muted-foreground"> → {{ log.user.username }} </span>
            </div>

            <Button size="sm" variant="ghost" @click="showDetails(log)"> Details </Button>
          </div>

          <div v-if="auditStore.logs.length === 0" class="text-center py-8 text-muted-foreground">
            No audit logs found
          </div>

          <div class="flex items-center justify-between pt-4 border-t">
            <p class="text-sm text-muted-foreground">
              Showing {{ auditStore.logs.length }} of {{ auditStore.total }} logs
            </p>
            <div class="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                :disabled="page === 1"
                @click="
                  () => {
                    page--
                    loadLogs()
                  }
                "
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="outline"
                :disabled="page * pageSize >= auditStore.total"
                @click="
                  () => {
                    page++
                    loadLogs()
                  }
                "
              >
                Next
              </Button>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
