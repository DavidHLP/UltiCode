# Draft: 修复 409 Conflict 错误 - create-initial 版本

## 问题描述
- **错误**: `POST http://localhost:9001/admin/problems/1/versions/create-initial` 返回 409 Conflict
- **调用位置**: `request.ts:366`, `problems.ts:408`, `useVersionHistory.ts:152`
- **错误信息**: `{status: 409, message: 'Request failed with status code 409', data: {...}}`

## 初步分析
- 409 Conflict 通常表示资源已存在，尝试创建冲突
- 端点路径: `/admin/problems/{id}/versions/create-initial`
- 可能是重复创建已存在的初始版本导致的冲突

## Root Cause (已确认)

**409 是后端有意返回的冲突检测**

```java
// ProblemVersionService.createInitialVersion:47
if (hasVersionHistory(problemId)) {
    throw new BusinessException(ErrorCode.CONFLICT, "Version history already exists for this problem");
}
```

**hasVersionHistory 检查**: `problem_version_history` 表中是否已存在该 problemId 的版本记录

**前端已有处理**:
```typescript
// useVersionHistory.ts:157-160
if (error instanceof ApiError && error.code === 409) {
    toast.info(t('problems.versionHistory.alreadyHasVersions'))  // 软提示
    loadVersions()
    emit('restored')
}
```

## 修复计划

### 后端修改
**文件**: `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemVersionService.java`
**位置**: `createInitialVersion` 方法 (lines 41-68)

**修改逻辑**:
```java
@Transactional
public ProblemVersionHistory createInitialVersion(Long problemId) {
    Problem problem = problemMapper.selectById(problemId);
    if (problem == null) {
        throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
    }

    // 幂等性改造：如果版本已存在，返回现有 version 1
    ProblemVersionHistory existing = getVersion1(problemId);
    if (existing != null) {
        log.info("Version 1 already exists for problem {}, returning existing", problemId);
        return existing;
    }

    // ... 创建新版本的逻辑保持不变
}
```

需要新增 `getVersion1(problemId)` 方法或利用现有查询。

### 前端修改
**文件**: `management/src/components/problems/useVersionHistory.ts`
**位置**: `createInitialSnapshot` 函数 (lines 149-168)

**修改逻辑**:
- 移除 `error.code === 409` 的特殊错误处理分支
- 因为后端不再抛出 409，前端只需处理成功和一般错误情况

## 状态
- [x] 修复方案已确认（幂等性改造）
- [ ] 后端修改 pending
- [ ] 前端修改 pending
- [ ] 验证测试 pending

## 探索任务
- 已在后台运行探索代理，查找相关代码
