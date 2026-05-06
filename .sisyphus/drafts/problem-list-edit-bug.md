# Bug调查: 问题列表编辑保存无效

## 问题描述
- **页面**: 管理后台 (localhost:9003) /problem-lists/list-concurrency/edit
- **现象**: 点击"保存更改"后，界面显示成功，但实际数据未改变
- **涉及字段**: 名称、描述、可见性(PUBLIC/FEATURED)、横幅标签、主题颜色、排序

## 调查方向
1. **前端**: 表单提交逻辑、API调用、请求体构造
2. **后端**: Update Controller/Service、字段映射、事务处理、权限检查

## 发现的关键线索

### 前端分析
- **API路径**: `PATCH /admin/problem-lists/{id}` ✅ 正确
- **请求体**: UpdateProblemListDto (name, description, isPublic, isFeatured, bannerTag, bannerTheme, bannerOrder)
- **表单组件**: `GeneralInfo.vue` 使用 vee-validate + zod
- **表单字段缺失**: 表单中没有 `slug` 字段和 `bannerIcon` 字段

### 后端分析
- **Admin Controller**: `AdminProblemListController` 处理 `/admin/problem-lists/{id}` ✅ 正确
- **权限**: `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` 
- **Update逻辑**: AdminProblemListServiceImpl.updateProblemList() → ProblemListServiceImpl.updateList()
- **字段更新**: 使用null检查，只有非null字段才会更新

### 已确认的关键问题

#### 问题1: 后端权限检查绕过（设计缺陷）
`AdminProblemListServiceImpl.updateProblemList` 传递 `list.getAuthorId()` 给 `ProblemListServiceImpl.updateList` 作为 userId：
```java
return problemListService.updateList(id, list.getAuthorId(), dto);
```
这导致 `ProblemListServiceImpl.updateList` 中的作者检查形同虚设：
```java
if (!list.getAuthorId().equals(userId)) {
    throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
}
```
因为比较的是 `authorId.equals(authorId)`，总是为true。

#### 问题2: 前端请求去重机制
`request.ts` 中有请求去重逻辑：
```javascript
const key = getRequestKey(config)  // method + url + params + data
if (pendingRequests.has(key)) {
    const controller = pendingRequests.get(key)!
    controller.abort()  // 取消之前的请求
    pendingRequests.delete(key)
}
```
如果用户快速点击保存，第二次请求会导致第一次请求被 abort，可能导致数据未保存。

#### 问题3: Admin Service 缺少 @Transactional
`AdminProblemListServiceImpl.updateProblemList` 没有 `@Transactional` 注解。虽然调用的 `ProblemListServiceImpl.updateList` 有 `@Transactional`，但在某些 Spring 代理场景下可能导致事务问题。

#### 问题4: 前端错误处理不当
`GeneralInfo.vue` 中 catch 块对所有错误使用相同的 toast：
```javascript
} catch {
    toast.error(t('problemLists.toast.createFailed'))  // 编辑也显示"创建失败"
}
```

#### 问题5: 编辑后数据未刷新
`updateList` 成功后，前端合并返回数据到 `currentList`，但没有重新获取完整的 `ProblemListDetail`（包含 problems 数组）。

## 根因诊断

**最可能的根因**: 
1. **请求去重导致 PATCH 请求被 abort**（如果是快速点击保存）
2. **后端事务未正确提交**（AdminServiceImpl 缺少 @Transactional）
3. **字段映射问题** - 某些字段为 undefined 时后端反序列化为 null，跳过更新

## 需要用户确认的信息
- [ ] 浏览器 Network 面板中 PATCH 请求的状态码
- [ ] Console 面板是否有错误/警告
- [ ] 请求体 Payload 内容
- [ ] 用户角色（ADMIN/SUPER_ADMIN）

## 相关文件
- `management/src/views/problem-lists/components/GeneralInfo.vue` - 表单组件
- `management/src/api/admin/problem-lists.ts` - API层
- `management/src/utils/request.ts` - 请求工具（含去重逻辑）
- `management/src/stores/admin/problem-lists.ts` - Pinia store
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminProblemListController.java` - Admin Controller
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java` - Admin Service
- `backend-spring/src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java` - 核心Service
- `backend-spring/src/main/java/com/ulticode/modules/problemlist/dto/UpdateProblemListDTO.java` - Update DTO 
