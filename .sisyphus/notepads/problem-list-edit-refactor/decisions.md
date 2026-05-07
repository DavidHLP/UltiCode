# Decisions

## Architecture
- Optimistic lock via `version` column (MyBatis-Plus `@Version`)
- Module-level partial updates: BasicInfo (name, desc), Visibility (isPublic, isFeatured), Banner (bannerTag, bannerTheme, bannerOrder)
- Auto-save composable: useDebounceFn + blur event, AbortController for request cancellation

## Frontend Module Structure
- BasicInfoSection.vue: name, description
- VisibilitySection.vue: isPublic, isFeatured
- BannerSection.vue: bannerTag, bannerTheme, bannerOrder
- useAutoSave.ts:通用composable
- useProblemListPermissions.ts: 权限检查

## API Design
- PATCH /problem-lists/{id} with partial payloads
- version field in request for optimistic lock check
- 409 Conflict on version mismatch
