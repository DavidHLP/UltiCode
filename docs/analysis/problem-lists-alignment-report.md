# Problem-Lists 前后端颗粒度对齐分析报告

> 生成日期: 2026-05-20
> 分析范围: `http://localhost:9003/problem-lists` 及其子页面
> 涉及模块: management (Vue3) + backend-spring (Spring Boot)

---

## 一、前端颗粒度 (Management Console)

| 页面/模块 | 文件 | 功能拆分 |
|---|---|---|
| **列表页** | `ProblemListsListView.vue` | 搜索、筛选(featured/visibility)、分页、批量选择、CRUD操作 |
| **创建页** | `ProblemListDetailView.vue` (create模式) | 仅 BasicInfoSection (name + description) |
| **编辑页** | `ProblemListDetailView.vue` (edit模式) | 4个独立Section，各自独立保存 |
| ├─ 基础信息 | `BasicInfoSection.vue` | name, description → 调用 `updateBasicInfo()` |
| ├─ 可见性 | `VisibilitySection.vue` | isPublic, isFeatured → 调用 `updateVisibility()` |
| ├─ Banner设置 | `BannerSection.vue` | bannerTag, bannerTheme, bannerOrder → 调用 `updateBanner()` |
| └─ 题目管理 | `ProblemsManager.vue` | 增删题目、调整排序 → 调用 `updateListProblems()` |

### 前端 API 层 (`management/src/api/admin/problem-lists.ts`)

前端已按功能拆分为以下接口：

```typescript
// 列表与详情
getLists(query: ProblemListQuery)     → GET    /admin/problem-lists
getList(id: string)                   → GET    /admin/problem-lists/{id}

// CRUD
createList(data: CreateProblemListDto) → POST   /admin/problem-lists
updateList(id, data: UpdateProblemListDto) → PATCH /admin/problem-lists/{id}
deleteList(id)                        → DELETE /admin/problem-lists/{id}

// 细粒度更新（当前全部复用通用 PATCH 端点）
updateBasicInfo(id, data)             → PATCH  /admin/problem-lists/{id}
updateVisibility(id, data)            → PATCH  /admin/problem-lists/{id}
updateBanner(id, data)                → PATCH  /admin/problem-lists/{id}

// 题目管理
updateListProblems(id, data)          → POST   /admin/problem-lists/{id}/problems
```

---

## 二、后端颗粒度 (Spring Boot)

| 层级 | 文件 | 颗粒度现状 |
|---|---|---|
| **Controller** | `AdminProblemListController.java` | **粗粒度** — 只有通用 `PATCH /{id}` |
| **Service (Admin)** | `AdminProblemListService.java` | 粗粒度 — `updateProblemList(id, dto, userId)` |
| **Service (Domain)** | `ProblemListService.java` | **细粒度** — `updateBasicInfo()`, `updateVisibility()`, `updateBanner()` |
| **ServiceImpl (Domain)** | `ProblemListServiceImpl.java` | **细粒度** — 已实现3个独立方法 |
| **DTOs** | `problemlist/dto/` | **细粒度** — `UpdateBasicInfoDTO`, `UpdateVisibilityDTO`, `UpdateBannerDTO` 已定义 |

### 后端 Admin Controller 端点 (`backend-spring/.../AdminProblemListController.java`)

```java
GET    /admin/problem-lists                  → getProblemLists(query)
GET    /admin/problem-lists/{id}             → getProblemListById(id)
POST   /admin/problem-lists                  → createProblemList(dto, userId)
PATCH  /admin/problem-lists/{id}             → updateProblemList(id, dto, userId)
DELETE /admin/problem-lists/{id}             → deleteProblemList(id)
POST   /admin/problem-lists/{id}/problems    → updateListProblems(id, dto)
```

---

## 三、核心不对齐问题

### 🔴 问题 1：Controller 与 Service 颗粒度倒挂

**现象：** `ProblemListService` (领域层) 已提供细粒度方法：

```java
// ProblemListService.java
ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto);
ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto);
ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto);
```

但 `AdminProblemListController` (接口层) 只暴露了一个粗粒度端点：

```java
@PatchMapping("/{id}")
public Result<ProblemListSummaryVO> updateProblemList(
    @PathVariable String id,
    @Valid @RequestBody UpdateProblemListDTO dto, ...)
```

**结果：** 前端调用的 `updateBasicInfo` / `updateVisibility` / `updateBanner` 最终都打到同一个 `PATCH /{id}` 端点上，字段级校验和语义意图丢失。

---

### 🟡 问题 2：DTO 字段长度校验不一致

| 字段 | 前端校验 | `UpdateBasicInfoDTO` | `UpdateProblemListDTO` |
|---|---|---|---|
| name | max 100 | `@Size(max=100)` | `@Size(max=100)` ✅ |
| description | 无 | `@Size(max=500)` | `@Size(max=1000)` ❌ |
| slug | 无 | `@Size(max=100)` | **字段不存在** ❌ |

**风险：** 两个 DTO 对 description 的长度限制不同（500 vs 1000）。

---

### 🟡 问题 3：前端 `CreateProblemListDto` 包含后端不认识的字段

前端定义：

```typescript
interface CreateProblemListDto {
  name: string
  description?: string
  slug?: string      // ← 后端 CreateProblemListDTO 无此字段
  isPublic?: boolean
  bannerTag?: string
  bannerIcon?: string
  bannerTheme?: string
  bannerOrder?: number
  authorId?: string
}
```

后端 `CreateProblemListDTO.java`：

```java
public class CreateProblemListDTO {
    @NotBlank @Size(max=100) private String name;
    @Size(max=1000) private String description;
    private Boolean isPublic = false;
    private String bannerTag;
    private String bannerIcon;
    private String bannerTheme;
    private Integer bannerOrder;
    // ❌ 没有 slug 字段
    // ❌ 没有 authorId 字段（从 header 取）
}
```

---

### 🟡 问题 4：权限控制 — 前后端不一致

前端 `useProblemListPermissions.ts`：

```typescript
PROBLEM_LIST_MANAGE_PROBLEMS: { action: 'MANAGE_PROBLEMS', resource: 'PROBLEM_LIST' }
```

后端 Controller：

```java
@PostMapping("/{id}/problems")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'MANAGE_PROBLEMS')")
```

**注意：** Spring Security 的 `hasAnyRole` 会自动加上 `ROLE_` 前缀。需要确认后端 `UserDetailsService` 是否将 `MANAGE_PROBLEMS` 作为 Role 加载，否则鉴权会失败。

---

## 四、建议对齐方案

### 方案 A：提升 Controller 颗粒度（推荐）

为 `AdminProblemListController` 增加3个细分端点，与前端对齐：

```java
@PatchMapping("/{id}/basic-info")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
public Result<ProblemListSummaryVO> updateBasicInfo(
    @PathVariable String id,
    @Valid @RequestBody UpdateBasicInfoDTO dto,
    @RequestHeader(value = "X-User-Id", required = false) String userId)

@PatchMapping("/{id}/visibility")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
public Result<ProblemListSummaryVO> updateVisibility(
    @PathVariable String id,
    @Valid @RequestBody UpdateVisibilityDTO dto,
    @RequestHeader(value = "X-User-Id", required = false) String userId)

@PatchMapping("/{id}/banner")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
public Result<ProblemListSummaryVO> updateBanner(
    @PathVariable String id,
    @Valid @RequestBody UpdateBannerDTO dto,
    @RequestHeader(value = "X-User-Id", required = false) String userId)
```

Service 层直接委托给已存在的 `ProblemListService.updateBasicInfo()` 等方法。

---

### 方案 B：降级前端颗粒度（不推荐）

将前端的 `updateBasicInfo` / `updateVisibility` / `updateBanner` 合并为一个通用的 `updateList` 调用。这会损失按区块保存的 UX 优势。

---

### 方案 C：保持现状 + 明确约定

如果认为当前"一个 PATCH 走天下"是可接受的：

1. 删除未使用的 `UpdateBasicInfoDTO`、`UpdateVisibilityDTO`、`UpdateBannerDTO`（避免维护负担）
2. 统一 `UpdateProblemListDTO` 的校验规则与前端期望一致
3. 前端停止发送 `slug` 和 `authorId` 字段（如果后端不支持）

---

## 五、i18n 颗粒度

i18n 前后端对齐情况良好：

- `management/src/i18n/locales/*/modules/problemLists.ts` 已完整覆盖
- 键名与 UI 组件中的 `t('problemLists.xxx')` 完全对应
- 中英文双语完整

---

## 六、测试覆盖

| 类型 | 文件 | 状态 |
|---|---|---|
| 单元测试 | `BannerSection.test.ts` | ✅ |
| 单元测试 | `BasicInfoSection.test.ts` | ✅ |
| 单元测试 | `VisibilitySection.test.ts` | ✅ |
| 单元测试 | `useProblemListPermissions.test.ts` | ✅ |
| E2E | `problem-list-edit.spec.ts` | ✅ |

---

## 七、核心文件清单

### 前端

| 文件 | 说明 |
|---|---|
| `management/src/views/problem-lists/ProblemListsListView.vue` | 列表页 |
| `management/src/views/problem-lists/ProblemListDetailView.vue` | 创建/编辑页壳 |
| `management/src/views/problem-lists/columns.ts` | DataTable 列定义 |
| `management/src/views/problem-lists/components/BasicInfoSection.vue` | 基础信息区块 |
| `management/src/views/problem-lists/components/VisibilitySection.vue` | 可见性区块 |
| `management/src/views/problem-lists/components/BannerSection.vue` | Banner 区块 |
| `management/src/views/problem-lists/components/ProblemsManager.vue` | 题目管理区块 |
| `management/src/api/admin/problem-lists.ts` | API 客户端 |
| `management/src/stores/admin/problem-lists.ts` | Pinia Store |
| `management/src/composables/useProblemListPermissions.ts` | 权限检查 |

### 后端

| 文件 | 说明 |
|---|---|
| `backend-spring/.../admin/controller/AdminProblemListController.java` | Admin REST 控制器 |
| `backend-spring/.../admin/service/AdminProblemListService.java` | Admin Service 接口 |
| `backend-spring/.../admin/service/impl/AdminProblemListServiceImpl.java` | Admin Service 实现 |
| `backend-spring/.../problemlist/service/ProblemListService.java` | 领域 Service 接口 |
| `backend-spring/.../problemlist/service/impl/ProblemListServiceImpl.java` | 领域 Service 实现 |
| `backend-spring/.../problemlist/dto/ProblemListSummaryVO.java` | 列表 VO |
| `backend-spring/.../problemlist/dto/ProblemListDetailVO.java` | 详情 VO |
| `backend-spring/.../problemlist/dto/CreateProblemListDTO.java` | 创建 DTO |
| `backend-spring/.../problemlist/dto/UpdateProblemListDTO.java` | 通用更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateBasicInfoDTO.java` | 基础信息更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateVisibilityDTO.java` | 可见性更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateBannerDTO.java` | Banner 更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateProblemListProblemsDTO.java` | 题目更新 DTO |
| `backend-spring/.../problemlist/entity/ProblemList.java` | MyBatis-Plus 实体 |

---

## 八、总结

当前 `problem-lists` 模块的**前端颗粒度细于后端 Controller 颗粒度**，形成了"前端细分保存、后端粗口接收"的不对称结构。核心建议是**为 Admin Controller 增加3个细分 PATCH 端点**（`/{id}/basic-info`、`/{id}/visibility`、`/{id}/banner`），直接复用已存在于 `ProblemListService` 中的细粒度方法，实现真正的前后端对齐。
