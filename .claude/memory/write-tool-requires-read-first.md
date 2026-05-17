---
name: write-tool-requires-read-first
description: Write tool requires existing files to be Read first before overwriting
metadata:
  type: feedback
---

## 规则

**写已存在的文件前必须先 Read。**

- 文件已存在且未被当前会话读取过 → Write 会失败，错误为 "File has not been read yet"
- 文件不存在 → Write 可直接创建新文件

## 为什么

Write 工具的保护机制：防止意外覆盖未在当前上下文中确认过的文件。

## 如何应用

每次要 Write 一个已存在的文件时，**必须先 Read**。即使知道文件内容、即使刚从 git 读 过、即使 compaction 之前读过——只要文件在当前会话的 Read 历史中不存在，就必须重新 Read。

简写：`Write(file)` = `Read(file)` + `Write(file, content)`，但仅限新文件场景可跳过 Read。
