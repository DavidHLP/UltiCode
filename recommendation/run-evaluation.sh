#!/bin/bash
# 离线评估一键执行脚本

set -e

# 切换到脚本所在目录
cd "$(dirname "$0")"

# 数据库配置
DB_URL="${DB_URL:-jdbc:mysql://localhost:23306/ulticode}"
DB_USER="${DB_USER:-ulticode}"
DB_PASS="${DB_PASSWORD:-}"
K="${K:-10}"

echo "============================================================"
echo "Building project..."
echo "============================================================"

# 构建项目（跳过测试）
mvn clean compile -DskipTests -q

echo ""
echo "============================================================"
echo "Building classpath..."
echo "============================================================"

# 构建依赖 classpath
CLASSPATH_FILE=$(mktemp)
mvn dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE" -pl recommend-core -q

# 组装完整 classpath
CLASSPATH="recommend-core/target/classes:$(cat "$CLASSPATH_FILE")"
rm -f "$CLASSPATH_FILE"

echo ""
echo "============================================================"
echo "Running offline evaluation..."
echo "============================================================"

# 运行评估
java -cp "$CLASSPATH" \
     -Ddb.url="$DB_URL" \
     -Ddb.user="$DB_USER" \
     -Ddb.password="$DB_PASS" \
     -Dk="$K" \
     com.ulticode.recommend.core.OfflineEvaluationRunner
