#!/bin/bash
set -e

echo "🔄 [1/5] 更新主仓库 main"
git pull origin main

echo "🔄 [2/5] 初始化并递归更新所有子模块"
git submodule update --init --recursive

echo "🔄 [3/5] 对所有子模块递归执行同步操作"
git submodule foreach --recursive '
  echo "➡ 同步子模块 $name"
  git fetch origin main || true
  git checkout main || true
  git pull origin main || true
  git add .
  git commit -m "同步子模块 $name 到 main 最新内容" || true
  git push origin main || true
'

echo "🔄 [4/5] 回到主仓库并更新引用"
git add .
git commit -m "同步所有（含嵌套）子模块引用到最新" || true

echo "🚀 [5/5] 推送主仓库更新"
git push origin main

echo "✅ 所有层级子模块同步完成！"
