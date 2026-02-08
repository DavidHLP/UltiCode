<script setup lang="ts">
import { h } from 'vue'
import {
  IconAlertTriangle,
  IconCircleCheckFilled,
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconFlagOff,
  IconFlask,
  IconBrackets,
  IconLoader,
  IconPencil,
  IconTrophy,
  IconCheck,
  IconSparkles,
  IconX,
  IconTrash,
} from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
} from '@/components/ui/dropdown-menu'
import type { ColumnDef } from '@tanstack/vue-table'
import type { Problem, Difficulty } from '@/api/admin/problems'
import { getDifficultyBadgeVariant, getDifficultyColor } from '@/lib/entities/problem'

interface Props {
  data: Problem[]
  loading: boolean
  canUpdateProblem: boolean
  canDeleteProblem: boolean
  viewProblem: (id: string) => void
  viewProblemCode: (id: string) => void
  viewProblemCases: (id: string) => void
  editProblem: (id: string) => void
  editProblemCode: (id: string) => void
  editProblemCases: (id: string) => void
  confirmDelete: (problem: Problem) => void
  publishProblem: (id: string) => void
  unpublishProblem: (id: string) => void
  flagProblem: (id: string) => void
  unflagProblem: (id: string) => void
}

const props = defineProps<Props>()

function getDifficultyIcon(difficulty: Difficulty) {
  switch (difficulty) {
    case 'EASY':
      return IconCheck
    case 'MEDIUM':
      return IconSparkles
    case 'HARD':
      return IconTrophy
    default:
      return IconFile
  }
}

const columns: ColumnDef<Problem>[] = [
  {
    accessorKey: 'title',
    header: () => 'Problem',
    cell: ({ row }) => {
      const problem = row.original
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'font-medium text-sm' }, problem.title),
        h('span', { class: 'text-muted-foreground text-xs' }, problem.slug),
      ])
    },
  },
  {
    accessorKey: 'difficulty',
    header: () => 'Difficulty',
    cell: ({ row }) => {
      const difficulty = row.getValue('difficulty') as Difficulty
      const icon = getDifficultyIcon(difficulty)
      const color = getDifficultyColor(difficulty)
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: `h-4 w-4 ${color}` }),
        h(Badge, { variant: getDifficultyBadgeVariant(difficulty) }, () => difficulty),
      ])
    },
  },
  {
    accessorKey: 'status',
    header: () => 'Status',
    cell: ({ row }) => {
      const status = row.getValue('status') as string
      const isSolved = status === 'solved'
      const isAttempted = status === 'attempted'
      const icon = isSolved ? IconCircleCheckFilled : undefined
      const variant = isSolved
        ? ('default' as const)
        : isAttempted
          ? ('secondary' as const)
          : ('outline' as const)
      const label = isSolved ? 'Solved' : isAttempted ? 'Attempted' : 'Todo'
      return h('div', { class: 'flex items-center gap-2' }, [
        icon
          ? h(icon, { class: 'h-4 w-4 text-emerald-500' })
          : h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' }),
        h(Badge, { variant }, () => label),
      ])
    },
  },
  {
    accessorKey: 'is_published',
    header: () => 'Published',
    cell: ({ row }) => {
      const isPublished = row.getValue('is_published') as boolean
      const isDeleted = row.original.is_deleted
      if (isDeleted) {
        return h(
          Badge,
          { variant: 'destructive' },
          { default: () => [h(IconX, { class: 'mr-1 h-3 w-3' }), 'Deleted'] },
        )
      }
      return h(
        Badge,
        { variant: isPublished ? 'default' : 'secondary' },
        {
          default: () => [
            isPublished
              ? h(IconCheck, { class: 'mr-1 h-3 w-3' })
              : h(IconEyeOff, { class: 'mr-1 h-3 w-3' }),
            isPublished ? 'Published' : 'Draft',
          ],
        },
      )
    },
  },
  {
    accessorKey: 'is_flagged',
    header: () => 'Flagged',
    cell: ({ row }) => {
      const isFlagged = row.original.is_flagged
      if (!isFlagged) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h(
        Badge,
        { variant: 'destructive', class: 'gap-1' },
        {
          default: () => [h(IconAlertTriangle, { class: 'h-3 w-3' }), 'Pending'],
        },
      )
    },
  },
  {
    accessorKey: 'submission_count',
    header: () => 'Submissions',
    cell: ({ row }) => {
      const count = row.original.submission_count || 0
      return h(
        'span',
        { class: 'text-muted-foreground text-sm tabular-nums' },
        count.toLocaleString(),
      )
    },
  },
  {
    accessorKey: 'tags',
    header: () => 'Tags',
    cell: ({ row }) => {
      const tags = row.original.tags || []
      if (tags.length === 0) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h(
        'div',
        { class: 'flex items-center gap-1 flex-wrap' },
        tags
          .slice(0, 3)
          .map((tag) =>
            h(Badge, { variant: 'outline', class: 'text-xs' }, { default: () => tag.label }),
          ),
      )
    },
  },
  {
    accessorKey: 'created_at',
    header: () => 'Created',
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as Date)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => 'Actions',
    cell: ({ row }) => {
      const problem = row.original
      return h(
        DropdownMenu,
        {},
        {
          default: () => [
            h(
              DropdownMenuTrigger,
              { asChild: true },
              {
                default: () =>
                  h(
                    Button,
                    { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, 'Open menu'),
                        h(IconDotsVertical, { class: 'h-4 w-4' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              { align: 'end' },
              {
                default: () => [
                  // View Sub-menu
                  h(
                    DropdownMenuSub,
                    {},
                    {
                      default: () => [
                        h(
                          DropdownMenuSubTrigger,
                          { class: 'gap-2' },
                          {
                            default: () => [h(IconEye, { class: 'h-4 w-4' }), 'View'],
                          },
                        ),
                        h(
                          DropdownMenuSubContent,
                          {},
                          {
                            default: () => [
                              h(
                                DropdownMenuItem,
                                { onClick: () => props.viewProblem(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconFile, { class: 'h-4 w-4' }),
                                      'Description',
                                    ]),
                                },
                              ),
                              h(
                                DropdownMenuItem,
                                { onClick: () => props.viewProblemCode(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconBrackets, { class: 'h-4 w-4' }),
                                      'Code',
                                    ]),
                                },
                              ),
                              h(
                                DropdownMenuItem,
                                { onClick: () => props.viewProblemCases(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconFlask, { class: 'h-4 w-4' }),
                                      'Test Cases',
                                    ]),
                                },
                              ),
                            ],
                          },
                        ),
                      ],
                    },
                  ),
                  // Edit Sub-menu
                  props.canUpdateProblem
                    ? h(
                        DropdownMenuSub,
                        {},
                        {
                          default: () => [
                            h(
                              DropdownMenuSubTrigger,
                              { class: 'gap-2' },
                              {
                                default: () => [h(IconPencil, { class: 'h-4 w-4' }), 'Edit'],
                              },
                            ),
                            h(
                              DropdownMenuSubContent,
                              {},
                              {
                                default: () => [
                                  h(
                                    DropdownMenuItem,
                                    { onClick: () => props.editProblem(problem.id) },
                                    {
                                      default: () =>
                                        h('div', { class: 'flex items-center gap-2' }, [
                                          h(IconFile, { class: 'h-4 w-4' }),
                                          'Description',
                                        ]),
                                    },
                                  ),
                                  h(
                                    DropdownMenuItem,
                                    { onClick: () => props.editProblemCode(problem.id) },
                                    {
                                      default: () =>
                                        h('div', { class: 'flex items-center gap-2' }, [
                                          h(IconBrackets, { class: 'h-4 w-4' }),
                                          'Code',
                                        ]),
                                    },
                                  ),
                                  h(
                                    DropdownMenuItem,
                                    { onClick: () => props.editProblemCases(problem.id) },
                                    {
                                      default: () =>
                                        h('div', { class: 'flex items-center gap-2' }, [
                                          h(IconFlask, { class: 'h-4 w-4' }),
                                          'Test Cases',
                                        ]),
                                    },
                                  ),
                                ],
                              },
                            ),
                          ],
                        },
                      )
                    : null,
                  h(DropdownMenuSeparator, {}),
                  // Flag/Unflag action
                  props.canUpdateProblem
                    ? h(
                        DropdownMenuItem,
                        {
                          onClick: () =>
                            problem.is_flagged
                              ? props.unflagProblem(problem.id)
                              : props.flagProblem(problem.id),
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              problem.is_flagged
                                ? h(IconFlagOff, { class: 'h-4 w-4 text-emerald-600' })
                                : h(IconFlag, { class: 'h-4 w-4 text-amber-600' }),
                              problem.is_flagged ? 'Unflag' : 'Flag',
                            ]),
                        },
                      )
                    : null,
                  h(DropdownMenuSeparator, {}),
                  props.canUpdateProblem
                    ? problem.is_published
                      ? h(
                          DropdownMenuItem,
                          { onClick: () => props.unpublishProblem(problem.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                                h(IconEyeOff, { class: 'h-4 w-4' }),
                                'Unpublish',
                              ]),
                          },
                        )
                      : h(
                          DropdownMenuItem,
                          { onClick: () => props.publishProblem(problem.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                                h(IconEye, { class: 'h-4 w-4' }),
                                'Publish',
                              ]),
                          },
                        )
                    : null,
                  props.canDeleteProblem
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => props.confirmDelete(problem) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              'Delete',
                            ]),
                        },
                      )
                    : null,
                ],
              },
            ),
          ],
        },
      )
    },
  },
]

defineExpose({ columns })
</script>

<template>
  <slot :columns="columns" />
</template>
