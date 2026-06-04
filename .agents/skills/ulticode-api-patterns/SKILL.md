---
name: ulticode-api-patterns
description: UltiCode project frontend-backend API integration patterns. Covers Result response format, snake_case/camelCase mapping, Console vs Management API styles, and common pitfalls. Trigger when adding or modifying API endpoints.
---

# UltiCode API 对接模式

## 后端 → 前端数据流

### 响应格式

后端所有 API 统一返回 `Result<T>` 格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "t-1717000000000"
}
```

### 前端解析

`request.ts` 拦截器自动解包 `Result.data`，前端拿到的直接就是 `data` 部分：

```typescript
// 后端返回: { code: 0, message: "success", data: { id: "xxx", ... }, traceId: "..." }
// 前端拿到: { id: "xxx", ... }
```

### 错误处理

- 业务错误：后端抛 `BusinessException`，返回 `{ code: xxx, message: "error msg" }`
- 前端拦截器检查 `code !== 0`，自动弹出 toast 提示
- 参数校验错误：返回 `{ code: 400, message: "Validation failed", data: { fieldName: "error msg" } }`

## snake_case ↔ camelCase 转换

- **后端 Entity**：字段名 `camelCase`，数据库列名 `snake_case`
- **后端 DTO 输出**：字段名 `camelCase`
- **前端**：接收 `camelCase`，内部也使用 `camelCase`
- **Console 前端**：部分 API 手动做 snake_case → camelCase 映射
- **Management 前端**：直接使用 camelCase，不做额外映射

## Console vs Management API 风格差异

| 维度 | Console | Management |
|------|---------|-----------|
| API 函数 | 直接 `apiGet/apiPost` | 封装为 `xxxApi.method()` |
| 类型定义 | 内联或简单接口 | 完整的 DTO/VO 类型 |
| 错误处理 | toast + 页面内处理 | DataTable + 通知 |
| 分页 | 手动构造参数 | `useDataTable` composable |

## 新增 API 端点流程

1. 后端定义 DTO (XxxDTO / XxxVO)
2. 后端实现 Controller + Service
3. 前端定义类型（Management 需完整 DTO 类型）
4. 前端实现 API 函数
5. 前端 i18n 翻译 key（如果涉及用户可见文本）

## 常见陷阱

1. **ghost types**: Management API 文件可能定义了尚无后端端点的类型（如 `UserWarning`, `UserBan`），不应删除
2. **Backend DTO enums**: 后端 DTO 用 `String` 表示枚举值，前端用 TypeScript enum
3. **DataTable i18n**: Management 的 DataTable 列头使用 `t('table.columnNames.{columnId}')`，columnId 为 camelCase
