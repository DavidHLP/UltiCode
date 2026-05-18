# `/admin/problems` 前后端代码颗粒度分析报告

> 生成时间：2026-05-18
> 分析页面：http://localhost:9003/problems

---

## 一、架构概览

`/admin/problems` 是 UltiCode 在线评判系统的**题目管理后台**，采用分层架构：

| 层级 | 技术栈 | 职责 |
|------|--------|------|
| 前端 | Vue 3 + TypeScript + Pinia | UI 展示、用户交互、状态管理 |
| 后端 | Spring Boot 3.2 + MyBatis-Plus | API 路由、业务逻辑、数据持久化 |

---

## 二、前端颗粒度分析

### 2.1 文件结构（按功能划分）

```
management/src/views/problems/
├── ProblemsListView.vue           # 题目列表主视图 (375行)
├── ProblemDetailView.vue          # 题目详情视图
├── ProblemEditView.vue            # 题目编辑视图 (仅路由分发)
├── ProblemCreateView.vue          # 题目创建视图
├── columns.ts                     # TanStack Table 列定义
├── tabs/                          # 详情页标签页
│   ├── OverviewTab.vue             # 概览标签
│   ├── DescriptionTab.vue          # 描述标签
│   ├── CodeTab.vue                # 代码标签
│   ├── CasesTab.vue               # 测试用例标签
│   └── AuditTab.vue               # 审计日志标签
├── view/                          # 只读视图
│   ├── ViewDescriptionView.vue
│   ├── ViewCodeView.vue
│   └── ViewCasesView.vue
├── edit/                          # 编辑视图
│   ├── EditDescriptionView.vue
│   ├── EditCodeView.vue
│   └── EditCasesView.vue
└── composables/                   # 业务逻辑组合式函数
    ├── useProblemFilters.ts       # 筛选器逻辑
    ├── useProblemActions.ts        # 操作逻辑 (330行)
    ├── useProblemColumns.ts        # 表格列定义 (256行)
    ├── useProblemTabData.ts        # 标签页数据
    ├── useProblemTab.ts            # 标签页状态
    └── useProblemEdit.ts           # 编辑逻辑

management/src/stores/admin/
└── problems.ts                     # Pinia Store (417行)

management/src/api/admin/
└── problems.ts                    # API 客户端 (493行)

management/src/components/problems/
├── BulkActionDialog.vue
├── BulkEditDialog.vue
├── ProblemImportDialog.vue
├── FlagInfoDialog.vue
└── VersionHistoryTimeline.vue
```

### 2.2 前端颗粒度评分

| 维度 | 评分 | 分析 |
|------|------|------|
| **功能内聚** | ⭐⭐⭐⭐⭐ | 按 Tab 切分清晰，view/edit/view 分离 |
| **文件大小** | ⭐⭐⭐⭐ | 最大文件 493 行，符合 <800 行规范 |
| **职责分离** | ⭐⭐⭐⭐⭐ | Composables 模式将逻辑与视图解耦 |
| **状态管理** | ⭐⭐⭐⭐ | Pinia Store 封装 + Tab 状态缓存 |
| **API 封装** | ⭐⭐⭐⭐⭐ | Tab 专属 API (`getHeader`, `getDescription`, `getCode`, `getCases`) |

### 2.3 前端亮点设计

**1. Tab 级数据懒加载与缓存**

```typescript
// stores/admin/problems.ts - 30秒 TTL 缓存
const CACHE_TTL_MS = 30_000

async function fetchTab<T>(
  tabKey: string,
  id: string,
  fetchFn: (id: string, signal: AbortSignal) => Promise<T>,
  forceRefresh = false,
): Promise<T | null> {
  const now = Date.now()
  const isStale = !state.loadedAt || (now - state.loadedAt) > CACHE_TTL_MS
  if (!forceRefresh && state.loadedId === id && state.data && !isStale) {
    return state.data  // 命中缓存
  }
  // ... fetch
}
```

**2. Composable 模式分离关注点**

```typescript
// useProblemActions.ts - 操作逻辑
export function useProblemActions(loadProblems: () => Promise<void>) {
  // 状态
  const deleteDialogOpen = ref(false)
  // 操作
  async function publishProblem(id: string) { ... }
  async function unpublishProblem(id: string) { ... }
}
```

**3. URL 作为状态**

```typescript
// useProblemFilters.ts - URL 同步
const debouncedUpdateUrl = useDebounceFn(() => {
  router.push({
    query: {
      search: searchQuery.value,
      difficulty: difficultyFilter.value,
      page: routePageIndex.value + 1,
    }
  })
}, 300)
```

---

## 三、后端颗粒度分析

### 3.1 文件结构

```
backend-spring/src/main/java/com/ulticode/modules/
├── admin/
│   ├── controller/
│   │   └── AdminProblemController.java    # Admin API 路由 (130行)
│   └── service/
│       ├── AdminProblemService.java       # 接口定义 (51行)
│       └── impl/
│           └── AdminProblemServiceImpl.java # Tab 数据聚合 (255行)
├── problem/
│   ├── controller/
│   │   ├── ProblemController.java         # C端 API
│   │   └── AdminProblemVersionController.java  # 版本控制
│   ├── service/
│   │   ├── ProblemService.java            # 业务逻辑接口 (138行)
│   │   ├── ProblemServiceImpl.java        # 业务实现
│   │   └── ProblemVersionService.java     # 版本管理
│   └── mapper/
│       ├── ProblemMapper.java             # MyBatis-Plus (64行)
│       ├── ProblemDetailMapper.java
│       ├── ProblemExampleMapper.java
│       ├── ProblemLanguageMapper.java
│       ├── ProblemTagMapper.java
│       └── ProblemTagRelationMapper.java
```

### 3.2 后端颗粒度评分

| 维度 | 评分 | 分析 |
|------|------|------|
| **分层清晰** | ⭐⭐⭐⭐⭐ | Controller → Service → Mapper 三层 |
| **文件大小** | ⭐⭐⭐⭐ | Controller 130行，Service 接口 51行，符合规范 |
| **接口设计** | ⭐⭐⭐⭐⭐ | Tab 级专用 VO (HeaderDataVO, DescriptionDataVO, CodeDataVO, CasesDataVO) |
| **复用性** | ⭐⭐⭐⭐ | Mapper 通过 MyBatis-Plus BaseMapper 复用 |
| **N+1 防护** | ⭐⭐⭐⭐ | `selectTagsByProblemIds` 批量查询解决 N+1 |

### 3.3 后端亮点设计

**1. Tab 级 API 端点**

```java
// AdminProblemController.java
@GetMapping("/{id}/header")       // 获取头部数据
@GetMapping("/{id}/description")  // 获取描述详情
@GetMapping("/{id}/code")         // 获取代码模板
@GetMapping("/{id}/cases")        // 获取测试用例
```

**2. DTO 模式分离展示与持久化**

```java
// DescriptionDataVO - 描述标签页专用
public class DescriptionDataVO {
    private String id;
    private String title;
    private String slug;
    private DetailInfo detail;     // 嵌套详情
    private List<TagVO> tags;
    private List<ExampleVO> examples;
}
```

**3. N+1 查询防护**

```java
// ProblemMapper.java
@Select("<script>" +
    "SELECT ptr.problem_id, pt.label as tag_name " +
    "FROM problem_tag_relations ptr " +
    "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
    "WHERE ptr.problem_id IN <foreach.../>" +
    "</script>")
List<ProblemTagDTO> selectTagsByProblemIds(@Param("problemIds") List<Long> problemIds);
```

---

## 四、前后端对比

| 对比维度 | 前端 | 后端 |
|----------|------|------|
| **代码量** | ~1700 行 (含 composables/store) | ~600 行 (不含测试) |
| **主要模式** | Composable + Pinia Store | Service + Mapper |
| **状态缓存** | 30s TTL + AbortController | 无缓存，按需查询 |
| **错误处理** | `getErrorContext` 分级处理 | `@ExceptionHandler` 全局处理 |
| **类型安全** | TypeScript 编译时检查 | Java 编译时检查 + OpenAPI 文档 |

---

## 五、颗粒度评估总结

| 评估项 | 前端 | 后端 |
|--------|------|------|
| **模块化** | 优秀 - composables 按职责拆分 | 优秀 - Tab VO 分离 |
| **可维护性** | 优秀 - <800 行/文件 | 优秀 - 接口+实现分离 |
| **可测试性** | 良好 - composables 可独立测试 | 优秀 - Mapper 接口易 Mock |
| **API 契约** | 优秀 - Tab API 与前端对应 | 优秀 - Swagger/OpenAPI 文档 |
| **性能考虑** | 良好 - 防抖 + AbortController | 优秀 - N+1 防护 + 批量查询 |

### 建议改进项

~~1. ~~**前端**：可考虑将 `useProblemActions.ts` 中的 `getErrorContext` 提取为独立工具函数~~ ✅ **已完成**
   - 已提取到 `@/utils/error.ts`
   - 已添加单元测试 `@/utils/__tests__/error.test.ts`
   - `useProblemActions.ts` 现在导入复用该工具函数

~~2. ~~**后端**：`AdminProblemServiceImpl` 使用 MapStruct 替代手写 Bean 映射~~ ✅ **已完成**
   - 新增 `AdminProblemMapper.java` MapStruct 接口
   - `AdminProblemServiceImpl` 从 255 行减少到约 120 行
   - 移除了手写 setter 代码，改用 `mapper.toXxx()` 方法
   - 代码行数减少约 50%，映射逻辑集中管理

3. **前后端**：可考虑引入 GraphQL 或 tRPC 简化 API 契约同步

   ### ⚠️ 成本收益分析结论

   **结论：不推荐迁移，理由如下**

   #### 技术不可行：tRPC

   | 限制项 | 影响 |
   |--------|------|
   | Node.js 后端必需 | tRPC 通过 TypeScript-to-TypeScript 类型共享实现端到端类型安全 |
   | Java 后端不兼容 | Spring Boot 无法集成 tRPC |
   | Vue 官方支持缺失 | 仅有第三方适配，生态薄弱 |

   **tRPC 仅适用于 TypeScript 全栈项目（Next.js/Nuxt + Node.js）**

   #### 成本过高：GraphQL

   | 成本项 | 工作量评估 |
   |--------|-----------|
   | Schema 定义（200+ 端点） | 2-3 周 |
   | Controller 改造 | 3-4 周 |
   | DataLoader 配置（N+1 防护） | 1-2 周 |
   | 前端改造（Apollo Client） | 2-3 周 |
   | 测试迁移 | 2 周 |
   | **总工期** | **3-4 个月** |

   | 收益项 | 量化 | 实际价值 |
   |--------|------|----------|
   | 减少网络请求 | 60-80% | 你的 Tab 级设计已分散请求，单页面请求数有限 |
   | 带宽节省 | 30-50% | 题目数据量小，收益不明显 |
   | 类型安全 | 40% 运行时错误减少 | OpenAPI + TypeScript 已提供足够保障 |

   #### 迁移风险

   - 破坏现有稳定系统
   - 团队需学习 GraphQL 查询语言和生态系统
   - 3-4 个月的开发机会成本

   #### 建议替代方案

   | 改进方向 | 收益 | 成本 |
   |----------|------|------|
   | OpenAPI 客户端生成 | 编译时类型安全 | 1 周 |
   | REST 响应压缩 | 性能优化 | 1 天 |
   | 请求批量处理 | 减少 RTT | 1 周 |

   **结论**：当前 REST + SpringDoc OpenAPI 架构已足够，无需引入 GraphQL/tRPC。

   > 参考来源：Spring GraphQL 官方文档、tRPC GitHub 文档
