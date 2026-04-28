# 修复 409 Conflict - create-initial 版本端点幂等性

## TL;DR

> **问题**: `POST /admin/problems/{id}/versions/create-initial` 在版本已存在时返回 409 Conflict
> **方案**: 版本已存在时返回现有 version 1，替代抛出 409
> **影响文件**: 后端 1 处，前端 1 处

---

## Context

### 原始问题
```
POST http://localhost:9001/admin/problems/1/versions/create-initial 409 (Conflict)
```

### 根因分析
- `ProblemVersionService.createInitialVersion` 在版本已存在时调用 `hasVersionHistory(problemId)` 检测并抛出 409
- `hasVersionHistory` 检查 `problem_version_history` 表中是否存在**任何**版本记录
- 前端已有 409 处理逻辑（软提示），但用户期望幂等性行为

### 相关文件
- `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemVersionService.java`
- `management/src/components/problems/useVersionHistory.ts`

---

## Work Objectives

### 必须修复
- [x] 后端 `createInitialVersion` 实现幂等性（版本已存在时返回现有版本）
- [x] 前端简化错误处理（移除 409 特殊分支）

### 验证条件
- [x] 后端编译通过
- [x] 连续两次调用 API 都返回 200（第二次返回现有版本，非 409）
- [x] 返回的 versionNumber 都是 1

---

## Verification Strategy

### 测试方法
```bash
# 1. 首次调用（应创建版本）
curl -s -X POST http://localhost:9001/admin/problems/1/versions/create-initial \
  -H "Content-Type: application/json" \
  -c /tmp/cookies.txt | jq .

# 2. 第二次调用（应返回现有版本，不报 409）
curl -s -X POST http://localhost:9001/admin/problems/1/versions/create-initial \
  -H "Content-Type: application/json" \
  -c /tmp/cookies.txt | jq .
```

---

## TODOs

### 1. 后端修改 - createInitialVersion 幂等性改造

**文件**: `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemVersionService.java`

**修改位置**: `createInitialVersion` 方法 (lines 40-68)

**What to do**:
1. 移除 `hasVersionHistory(problemId)` 检查和 409 抛出逻辑
2. 替换为精确查询 version 1 的逻辑：调用 `getVersion1(problemId)` 检查是否存在
3. 存在则返回现有 version 1，不存在则创建新版本
4. 新增私有方法 `getVersion1(Long problemId)`

**当前代码** (有问题):
```java
if (hasVersionHistory(problemId)) {
    throw new BusinessException(ErrorCode.CONFLICT, "Version history already exists for this problem");
}
```

**修改为**:
```java
// Idempotent: return existing version 1 if already exists
ProblemVersionHistory existingVersion1 = getVersion1(problemId);
if (existingVersion1 != null) {
    log.info("Version 1 already exists for problem {}, returning existing", problemId);
    return existingVersion1;
}
```

**新增方法** (在 `hasVersionHistory` 方法之后，约 line 148):
```java
private ProblemVersionHistory getVersion1(Long problemId) {
    LambdaQueryWrapper<ProblemVersionHistory> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(ProblemVersionHistory::getProblemId, problemId)
            .eq(ProblemVersionHistory::getVersionNumber, 1)
            .eq(ProblemVersionHistory::getChangeType, CHANGE_TYPE_INITIAL_CREATE);
    return versionHistoryMapper.selectOne(queryWrapper);
}
```

**References**:
- `ProblemVersionService.java:41-68` - 现有 `createInitialVersion` 方法
- `ProblemVersionService.java:143-148` - `hasVersionHistory` 方法作为参考
- `ProblemVersionHistory.java` - entity 类定义

**Must NOT do**:
- 不要保留 409 冲突逻辑
- 不要修改其他方法的签名

**QA Scenarios**:
```
Scenario: 首次调用创建版本
  Tool: Bash (curl)
  Preconditions: problemId=1 无版本历史
  Steps:
    1. curl -s -X POST http://localhost:9001/admin/problems/1/versions/create-initial -H "Content-Type: application/json" -c /tmp/cookies.txt
  Expected Result: HTTP 200, 返回 versionNumber=1 的版本
  Evidence: .sisyphus/evidence/fix-409-first-call.json

Scenario: 重复调用返回现有版本
  Tool: Bash (curl)
  Preconditions: problemId=1 已有 version 1
  Steps:
    1. curl -s -X POST http://localhost:9001/admin/problems/1/versions/create-initial -H "Content-Type: application/json" -c /tmp/cookies.txt
  Expected Result: HTTP 200, 返回 versionNumber=1 的现有版本（不是 409）
  Evidence: .sisyphus/evidence/fix-409-second-call.json
```

---

### 2. 前端修改 - 简化错误处理

**文件**: `management/src/components/problems/useVersionHistory.ts`

**修改位置**: `createInitialSnapshot` 函数 (lines 149-168)

**What to do**:
1. 移除 `error.code === 409` 的特殊 catch 分支
2. 简化为统一错误处理

**当前代码**:
```typescript
async function createInitialSnapshot() {
    createInitialLoading.value = true
    try {
      await problemsApi.createInitialVersion(props.problemId)
      toast.success(t('problems.versionHistory.createInitialSuccess'))
      loadVersions()
      emit('restored')
    } catch (error) {
      if (error instanceof ApiError && error.code === 409) {
        toast.info(t('problems.versionHistory.alreadyHasVersions'))
        loadVersions()
        emit('restored')
      } else {
        console.error('Failed to create initial version:', error)
        toast.error(t('problems.versionHistory.createInitialError'))
      }
    } finally {
      createInitialLoading.value = false
    }
  }
```

**修改为**:
```typescript
async function createInitialSnapshot() {
    createInitialLoading.value = true
    try {
      await problemsApi.createInitialVersion(props.problemId)
      toast.success(t('problems.versionHistory.createInitialSuccess'))
      loadVersions()
      emit('restored')
    } catch (error) {
      console.error('Failed to create initial version:', error)
      toast.error(t('problems.versionHistory.createInitialError'))
    } finally {
      createInitialLoading.value = false
    }
  }
```

**References**:
- `useVersionHistory.ts:149-168` - 现有 `createInitialSnapshot` 函数

**Must NOT do**:
- 不要修改 API 调用参数
- 不要移除 loading 状态管理

**QA Scenarios**:
```
Scenario: 成功创建版本后显示正确提示
  Tool: Playwright (前端测试)
  Preconditions: 问题无版本历史
  Steps:
    1. 导航到问题版本历史页面
    2. 点击创建初始版本按钮
    3. 验证成功 toast 显示
  Expected Result: success toast 可见
  Evidence: .sisyphus/evidence/fix-409-frontend-success.png
```

---

## Final Verification Wave

- [x] F1. 后端编译: `cd backend-spring && ./mvnw compile -q`
- [x] F2. API 幂等性测试（连续两次调用）
- [x] F3. 前端功能验证

---

## Commit Strategy

- **Commit**: YES
- **Message**: `fix(problem): make create-initial-version idempotent`
- **Files**: `ProblemVersionService.java`, `useVersionHistory.ts`
- **Pre-commit**: `cd backend-spring && ./mvnw compile -q`

---

## Success Criteria

1. `POST /admin/problems/{id}/versions/create-initial` 首次调用返回 200 + 新建版本
2. 第二次调用返回 200 + 现有版本（非 409 Conflict）
3. 前端移除 409 特殊处理后功能正常
