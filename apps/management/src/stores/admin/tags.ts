import { defineStore } from 'pinia'
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
  const total = collection.total
  const isLoading = collection.isLoading
  const error = collection.error
  const fetchTags = collection.fetch

  async function fetchTag(id: string, type: TagType) {
    isLoading.value = true
    error.value = null
    try {
      const tag = await tagsApi.getTag(id, type)
      return tag
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to fetch tag'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createTag(data: CreateTagDto) {
    isLoading.value = true
    error.value = null
    try {
      const newTag = await tagsApi.createTag(data)
      // Optimistically add to list if it matches current view, but simplest is to reload
      return newTag
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to create tag'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateTag(id: string, data: UpdateTagDto) {
    isLoading.value = true
    error.value = null
    try {
      const updatedTag = await tagsApi.updateTag(id, data)
      const index = tags.value.findIndex((t) => t.id === id)
      if (index !== -1) {
        tags.value[index] = { ...tags.value[index], ...updatedTag }
      }
      return updatedTag
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to update tag'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function deleteTag(id: string, type: TagType) {
    isLoading.value = true
    error.value = null
    try {
      await tagsApi.deleteTag(id, type)
      tags.value = tags.value.filter((t) => t.id !== id)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to delete tag'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function mergeTag(data: { sourceId: string; targetTagId: string; type: TagType }) {
    isLoading.value = true
    error.value = null
    try {
      await tagsApi.mergeTag(data)
      // Remove source tag from list locally
      tags.value = tags.value.filter((t) => t.id !== data.sourceId)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to merge tags'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  return {
    items: collection.items,
    fetch: collection.fetch,
    tags,
    total,
    isLoading,
    error,
    fetchTags,
    fetchTag,
    createTag,
    updateTag,
    deleteTag,
    mergeTag,
  }
})
