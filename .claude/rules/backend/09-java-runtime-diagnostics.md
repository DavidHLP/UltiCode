---
paths:
  - "**/*.java"
description: 运行时诊断工具（Arthas 等）的安全使用约束：限量、只读、收敛观察点
---

# Java 运行时诊断安全规约

> 本文件补充 `01-java-programming.md`，定义在 Spring Boot / 任意 JVM 上使用 Arthas 或同类运行时诊断工具的强制约束。

## 适用范围

凡需在**生产 / 预生产 / 共享测试**环境的 JVM 上执行 `arthas-boot.jar`、BTrace、Byteman 等字节码注入型诊断工具的场景均适用。本地单人开发环境可酌情放宽。

## 强制约束

1. **【强制】** `watch` / `trace` / `monitor` / `tt` 必须显式携带 `-n N` 限制执行次数（N 通常 ≤ 5）；禁止无 `-n` 的「无限执行」。
2. **【强制】** `vmtool --action getInstances` 必须显式携带 `-l N` 限制实例数量（N 通常 ≤ 5）；禁止直接输出完整 `getBeanDefinitionNames()`。
3. **【强制】** 优先使用只读探测 (`sc -d`、`containsBean`、`containsBeanDefinition`、`getBeanNamesForType`、`getBeansOfType(...).keySet()`)，禁止在未确认 Bean 是否存在的情况下直接 `getBean()` —— 后者会强制触发懒加载 Bean 的初始化，可能在生产环境带来 `@PostConstruct` 副作用。
4. **【强制】** 观察点选择「确定会被请求线程调用」的方法（Controller 入口、`Filter`/`Interceptor`、`@PostConstruct` 之外的非热点 Service），禁止在 `getById`、`listXxx` 等高频热点方法上无差别 `watch`。
5. **【强制】** 渐进收敛：先 `dashboard` 把握整体 → 再 `thread -n N` 定位热点 → 再对**精确类名+方法名** `stack` / `trace` → 必要时再 `watch`；禁止跳过前面的步骤直接对宽泛模式做 `trace`。
6. **【推荐】** 跨 ClassLoader 引用类时，先用 `classloader` 查 `classLoaderHash`，再在 `vmtool` / `ognl` 上加 `--classLoader <hash>` 重试，避免 `ClassNotFound` 反复试错污染日志。
7. **【推荐】** 诊断结论应包含：现象+证据（dashboard 摘要 / 线程堆栈关键片段）、初步定位（计算/锁/GC/日志等类别）、下一步建议（精确类名+方法名）。

## 反例

- `watch com.ulticode.modules.user.service.UserService * '{params, returnObj}'` （无 `-n`）→ 高频方法上无上限观测，线上延迟飙升。
- `getBean("fooService")` 而未先用 `containsBean` 验证 → 触发懒加载 Bean 实例化，可能执行 `@PostConstruct` 中的发邮件、发 MQ、远程调用等副作用。
- `vmtool --action getInstances --className ...AbstractApplicationContext --express 'instances[0].getBeanDefinitionNames()'` （无 `-l`）→ 数百个 Bean 名一次性打印，刷屏。
