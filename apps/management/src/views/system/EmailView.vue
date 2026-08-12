<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { toast } from 'vue-sonner'
import { emailApi } from '@/api/admin/email'
import type { EmailLog, EmailTemplate, EmailStats } from '@/api/admin/email'
import { Button } from '@/components/ui/button'
import { SemanticBadge, type SemanticColor } from '@/components/ui/terminal'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  IconMail,
  IconFileText,
  IconRefresh,
  IconSend,
  IconLoader2,
  IconCheck,
  IconX,
  IconClock,
  IconPlus,
  IconPencil,
  IconTrash,
} from '@tabler/icons-vue'

const { t } = useI18n()

// State
const loading = ref(true)
const activeTab = ref('logs')
const logs = ref<EmailLog[]>([])
const templates = ref<EmailTemplate[]>([])
const stats = ref<EmailStats | null>(null)
const isLoaded = ref(false)

// Send email dialog
const showSendDialog = ref(false)
const sending = ref(false)
const sendForm = ref({
  to: '',
  subject: '',
  html: '',
  templateId: '',
})

// Template dialog
const showTemplateDialog = ref(false)
const savingTemplate = ref(false)
const editingTemplate = ref<EmailTemplate | null>(null)
const templateForm = ref({
  name: '',
  subject: '',
  body: '',
  variables: '',
})

// Methods
async function loadData() {
  loading.value = true
  try {
    const [logsRes, templatesRes, statsRes] = await Promise.all([
      emailApi.getLogs({ limit: 50 }),
      emailApi.getTemplates(),
      emailApi.getStats(),
    ])
    logs.value = logsRes.items
    templates.value = templatesRes
    stats.value = statsRes
  } catch (error) {
    console.error('Failed to load email data:', error)
    toast.error(t('system.email.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openSendDialog(template?: EmailTemplate) {
  sendForm.value = {
    to: '',
    subject: template?.subject ?? '',
    html: template?.body ?? '',
    templateId: template?.id ?? '',
  }
  showSendDialog.value = true
}

async function sendEmail() {
  if (!sendForm.value.to || !sendForm.value.subject) {
    toast.error(t('system.email.validation.required'))
    return
  }

  sending.value = true
  try {
    await emailApi.sendEmail({
      to: sendForm.value.to,
      subject: sendForm.value.subject,
      html: sendForm.value.html,
      templateId: sendForm.value.templateId || undefined,
    })
    toast.success(t('system.email.toast.sendSuccess'))
    showSendDialog.value = false
    await loadData()
  } catch (error) {
    console.error('Failed to send email:', error)
    toast.error(t('system.email.toast.sendFailed'))
  } finally {
    sending.value = false
  }
}

function openCreateTemplateDialog() {
  editingTemplate.value = null
  templateForm.value = {
    name: '',
    subject: '',
    body: '',
    variables: '',
  }
  showTemplateDialog.value = true
}

function openEditTemplateDialog(template: EmailTemplate) {
  editingTemplate.value = template
  templateForm.value = {
    name: template.name,
    subject: template.subject,
    body: template.body,
    variables: (template.variables as string[])?.join(', ') ?? '',
  }
  showTemplateDialog.value = true
}

async function saveTemplate() {
  if (!templateForm.value.name || !templateForm.value.subject || !templateForm.value.body) {
    toast.error(t('system.email.validation.required'))
    return
  }

  savingTemplate.value = true
  try {
    const data = {
      name: templateForm.value.name,
      subject: templateForm.value.subject,
      body: templateForm.value.body,
      variables: templateForm.value.variables
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean),
    }

    if (editingTemplate.value) {
      await emailApi.updateTemplate(editingTemplate.value.id, data)
      toast.success(t('system.email.toast.updateSuccess'))
    } else {
      await emailApi.createTemplate(data)
      toast.success(t('system.email.toast.createSuccess'))
    }

    showTemplateDialog.value = false
    await loadData()
  } catch (error) {
    console.error('Failed to save template:', error)
    toast.error(t('system.email.toast.saveFailed'))
  } finally {
    savingTemplate.value = false
  }
}

async function deleteTemplate(template: EmailTemplate) {
  if (!confirm(t('system.email.deleteConfirm', { name: template.name }))) return

  try {
    await emailApi.deleteTemplate(template.id)
    toast.success(t('system.email.toast.deleteSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to delete template:', error)
    toast.error(t('system.email.toast.deleteFailed'))
  }
}

function getStatusIcon(status: string) {
  switch (status) {
    case 'SENT':
      return IconCheck
    case 'PENDING':
      return IconClock
    case 'FAILED':
      return IconX
    default:
      return IconClock
  }
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'SENT':
      return 'text-foreground-strong'
    case 'PENDING':
      return 'text-foreground-strong'
    case 'FAILED':
      return 'text-foreground-strong'
    default:
      return 'text-foreground-muted'
  }
}

function getStatusBadgeColor(status: string): SemanticColor {
  switch (status) {
    case 'SENT':
      return 'success'
    case 'FAILED':
      return 'error'
    default:
      return 'neutral'
  }
}

// Lifecycle
onMounted(async () => {
  await loadData()
  isLoaded.value = true
})
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="space-y-1">
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('system.email.title') }}
          </h1>
          <p class="text-xs text-[var(--foreground-muted)]">{{ t('system.email.description') }}</p>
        </div>
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)] transition-colors"
            :disabled="loading"
            @click="loadData"
          >
            <IconLoader2 v-if="loading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconRefresh v-else class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('common.refresh') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)] transition-colors"
            @click="openSendDialog()"
          >
            <IconSend class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('system.email.sendEmail') }}</span>
          </Button>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      :class="[
        'mt-6 space-y-6 transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.email.stats.total') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold">{{ stats?.total ?? 0 }}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.email.stats.sent') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold text-foreground-strong">{{ stats?.sent ?? 0 }}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.email.stats.pending') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold text-foreground-strong">{{ stats?.pending ?? 0 }}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.email.stats.failed') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold text-foreground-strong">{{ stats?.failed ?? 0 }}</div>
          </CardContent>
        </Card>
      </div>

      <!-- Tabs -->
      <Tabs v-model="activeTab">
        <TabsList>
          <TabsTrigger value="logs">
            <IconMail class="h-4 w-4 mr-1" />
            {{ t('system.email.tabs.logs') }}
          </TabsTrigger>
          <TabsTrigger value="templates">
            <IconFileText class="h-4 w-4 mr-1" />
            {{ t('system.email.tabs.templates') }}
          </TabsTrigger>
        </TabsList>

        <!-- Logs Tab -->
        <TabsContent value="logs" class="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{{ t('system.email.logs.title') }}</CardTitle>
            </CardHeader>
            <CardContent>
              <div v-if="loading" class="flex items-center justify-center py-8">
                <IconLoader2 class="h-6 w-6 animate-spin text-muted-foreground" />
              </div>

              <div v-else-if="logs.length === 0" class="text-center py-8 text-muted-foreground">
                {{ t('system.email.logs.noLogs') }}
              </div>

              <div v-else class="space-y-4">
                <div
                  v-for="log in logs"
                  :key="log.id"
                  class="flex items-center justify-between p-4 rounded-none border bg-card"
                >
                  <div class="flex items-center gap-4">
                    <component
                      :is="getStatusIcon(log.status)"
                      :class="['h-5 w-5', getStatusColor(log.status)]"
                    />
                    <div>
                      <div class="font-medium">{{ log.subject }}</div>
                      <div class="text-sm text-muted-foreground">
                        {{ t('system.email.to') }}: {{ log.recipient }} |
                        {{ t('system.email.createdAt') }}:
                        {{ formatDateTimeByLocale(log.created_at) }}
                      </div>
                      <div v-if="log.error" class="text-sm text-foreground-strong mt-1">
                        {{ log.error }}
                      </div>
                    </div>
                  </div>
                  <SemanticBadge :color="getStatusBadgeColor(log.status)">
                    {{ t(`system.email.status.${log.status}`, log.status) }}
                  </SemanticBadge>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <!-- Templates Tab -->
        <TabsContent value="templates" class="mt-4">
          <Card>
            <CardHeader class="flex flex-row items-center justify-between">
              <CardTitle>{{ t('system.email.templates.title') }}</CardTitle>
              <Button size="sm" @click="openCreateTemplateDialog">
                <IconPlus class="h-4 w-4 mr-1" />
                {{ t('system.email.templates.create') }}
              </Button>
            </CardHeader>
            <CardContent>
              <div v-if="loading" class="flex items-center justify-center py-8">
                <IconLoader2 class="h-6 w-6 animate-spin text-muted-foreground" />
              </div>

              <div
                v-else-if="templates.length === 0"
                class="text-center py-8 text-muted-foreground"
              >
                {{ t('system.email.templates.noTemplates') }}
              </div>

              <div v-else class="space-y-4">
                <div
                  v-for="template in templates"
                  :key="template.id"
                  class="flex items-center justify-between p-4 rounded-none border bg-card hover:bg-muted/50 transition-colors"
                >
                  <div>
                    <div class="font-medium">{{ template.name }}</div>
                    <div class="text-sm text-muted-foreground">{{ template.subject }}</div>
                  </div>
                  <div class="flex items-center gap-2">
                    <Button variant="ghost" size="sm" @click="openSendDialog(template)">
                      <IconSend class="h-4 w-4" />
                    </Button>
                    <Button variant="ghost" size="sm" @click="openEditTemplateDialog(template)">
                      <IconPencil class="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      class="text-destructive hover:text-destructive"
                      @click="deleteTemplate(template)"
                    >
                      <IconTrash class="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <!-- Send Email Dialog -->
      <Dialog v-model:open="showSendDialog">
        <DialogContent class="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{{ t('system.email.sendEmail') }}</DialogTitle>
          </DialogHeader>
          <div class="space-y-4 py-4">
            <div>
              <Label>{{ t('system.email.form.to') }}</Label>
              <Input v-model="sendForm.to" type="email" placeholder="user@example.com" />
            </div>
            <div>
              <Label>{{ t('system.email.form.subject') }}</Label>
              <Input
                v-model="sendForm.subject"
                :placeholder="t('system.email.form.subjectPlaceholder')"
              />
            </div>
            <div>
              <Label>{{ t('system.email.form.body') }}</Label>
              <Textarea
                v-model="sendForm.html"
                :placeholder="t('system.email.form.bodyPlaceholder')"
                class="min-h-[200px] font-mono"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showSendDialog = false">
              {{ t('common.cancel') }}
            </Button>
            <Button :disabled="sending" @click="sendEmail">
              <IconLoader2 v-if="sending" class="h-4 w-4 mr-1 animate-spin" />
              <IconSend v-else class="h-4 w-4 mr-1" />
              {{ t('system.email.send') }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <!-- Template Dialog -->
      <Dialog v-model:open="showTemplateDialog">
        <DialogContent class="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {{
                editingTemplate
                  ? t('system.email.templates.edit')
                  : t('system.email.templates.create')
              }}
            </DialogTitle>
          </DialogHeader>
          <div class="space-y-4 py-4">
            <div>
              <Label>{{ t('system.email.form.name') }}</Label>
              <Input
                v-model="templateForm.name"
                :placeholder="t('system.email.form.namePlaceholder')"
              />
            </div>
            <div>
              <Label>{{ t('system.email.form.subject') }}</Label>
              <Input
                v-model="templateForm.subject"
                :placeholder="t('system.email.form.subjectPlaceholder')"
              />
            </div>
            <div>
              <Label>{{ t('system.email.form.body') }}</Label>
              <Textarea
                v-model="templateForm.body"
                :placeholder="t('system.email.form.bodyPlaceholder')"
                class="min-h-[200px] font-mono"
              />
            </div>
            <div>
              <Label>{{ t('system.email.form.variables') }}</Label>
              <Input
                v-model="templateForm.variables"
                :placeholder="t('system.email.form.variablesPlaceholder')"
              />
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('system.email.form.variablesHelp') }}
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showTemplateDialog = false">
              {{ t('common.cancel') }}
            </Button>
            <Button :disabled="savingTemplate" @click="saveTemplate">
              <IconLoader2 v-if="savingTemplate" class="h-4 w-4 mr-1 animate-spin" />
              {{ t('common.save') }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </div>
</template>
