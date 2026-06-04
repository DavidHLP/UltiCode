---
name: arthas-springcontext-issues-resolve
description: Diagnose Spring ApplicationContext, Bean injection, and configuration issues using Arthas. Trigger when troubleshooting NoSuchBeanDefinitionException, NoUniqueBeanDefinitionException, or configuration injection failures.
---

# Spring Context / Bean 排查指南

原则：
- **先只读查询**（contains/beanNames/type/environment），避免直接 `getBean()` 触发 Bean 初始化产生副作用。
- **严格限量**：`vmtool -l` 控制实例数量；避免无条件输出完整 `getBeanDefinitionNames()`。

## 1) 获取并挑选正确的 ApplicationContext

优先尝试获取常见的 Spring Boot Context：

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 5
```

如果获取到多个对象，从 classloader 判断：
1. 应用的 ClassLoader 通常包含 `LaunchedURLClassLoader`
2. 应用的 ClassLoader 绝不是 `com.taobao.pandora.service.loader.ModuleClassLoader`

## 2) 获取配置项的值与来源

只看值（示例：`server.port`）：

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express 'instances[0].getEnvironment().getProperty("server.port")'
```

获取来源：

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express '#env=instances[0].getEnvironment(), #ps=#env.getPropertySources().get("configurationProperties"), #ps.findConfigurationProperty("server.port")'
```

## 3) 按 Bean Name 验证是否存在（不触发初始化）

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express 'instances[0].containsBean("fooService")'
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express 'instances[0].containsLocalBean("fooService")'
```

判读：
- `containsBean=true` 但 `containsLocalBean=false`：Bean 可能来自**父 Context**。
- `containsBean=false` 且确定应该存在：检查是否选错 Context、`@Profile/@Conditional`、配置项是否生效。

## 4) 在 Spring Context 里搜索 Bean（按关键词过滤）

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express '#ctx=instances[0], #names=@java.util.Arrays@asList(#ctx.getBeanDefinitionNames()), #m=#names.{? #this.toLowerCase().contains("order")}, #m.subList(0, @java.lang.Math@min(#m.size(), 50))'
```

## 5) 按类型查找 Bean

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express 'instances[0].getBeanNamesForType(@com.foo.OrderService@class)'
```

若返回多个候选，只看候选名称：

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express 'instances[0].getBeansOfType(@com.foo.OrderService@class).keySet()'
```

提示：若怀疑代理导致类型不匹配，优先按**接口类型**查询。

## 6) 查看 BeanDefinition（来源/工厂方法/作用域）

```bash
vmtool --action getInstances --className org.springframework.context.support.AbstractApplicationContext -l 1 --express '#ctx=instances[0], #bf=#ctx.getBeanFactory(), #bd=#bf.getBeanDefinition("fooService")'
```
