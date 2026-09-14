import { defineStore } from 'pinia'
import { ref, readonly } from 'vue'
import {
  tagsApi,
  type Tag,
  type TagQuery,
  type CreateTagDto,
  type UpdateTagDto,
  TagType,
} from '@/api/admin/tags'
import { createCollectionSlice } from '@/stores/createCollectionSlice'

export const useTagsStore = defineStore('admin-tags', () => {
  const collection = createCollectionSlice<Tag, TagQuery>({
    load: async (query = {}) => {
      const response = await tagsApi.getTags(query)
      return { items: response.data, total: response.total }
    },
  })
  const tags = collection.items
  const readonlyTags = readonly(tags) as Readonly<typeof tags>
  const total = readonly(collection.total) as Readonly<typeof collection.total>
  const isLoading = readonly(collection.isLoading) as Readonly<typeof collection.isLoading>
  const error = readonly(collection.error) as Readonly<typeof collection.error>
  const fetchTags = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)

  async function fetchTag(id: string, type: TagType) {
    operationLoading.value = true
    operationError.value = null
    try {
      const tag = await tagsApi.getTag(id, type)
      return tag
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to fetch tag'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function createTag(data: CreateTagDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const newTag = await tagsApi.createTag(data)
      // Optimistically add to list if it matches current view, but simplest is to reload
      return newTag
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to create tag'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function updateTag(id: string, data: UpdateTagDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const updatedTag = await tagsApi.updateTag(id, data)
      collection.updateItems((current) =>
        current.map((currentTag) =>
          currentTag.id === id ? { ...currentTag, ...updatedTag } : currentTag,
        ),
      )
      return updatedTag
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to update tag'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function deleteTag(id: string, type: TagType) {
    operationLoading.value = true
    operationError.value = null
    try {
      await tagsApi.deleteTag(id, type)
      const removed = tags.value.some((tag) => tag.id === id)
      collection.updateItems((current) => current.filter((tag) => tag.id !== id))
      if (removed) collection.setTotal(Math.max(0, total.value - 1))
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to delete tag'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function mergeTag(data: { sourceId: string; targetTagId: string; type: TagType }) {
    operationLoading.value = true
    operationError.value = null
    try {
      await tagsApi.mergeTag(data)
      // Remove source tag from list locally
      const removed = tags.value.some((tag) => tag.id === data.sourceId)
      collection.updateItems((current) => current.filter((tag) => tag.id !== data.sourceId))
      if (removed) collection.setTotal(Math.max(0, total.value - 1))
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to merge tags'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  function clearError() {
    collection.clearError()
    operationError.value = null
  }

  return {
    items: readonlyTags,
    fetch: collection.fetch,
    tags: readonlyTags,
    total,
    isLoading,
    error,
    operationLoading,
    operationError,
    fetchTags,
    fetchTag,
    createTag,
    updateTag,
    deleteTag,
    mergeTag,
    clearError,
  }
})
