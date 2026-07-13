#!/usr/bin/env bash
# 本地启动后端（Spring Boot），不启用 Nacos，使用本地 PostgreSQL + Redis。
# 用法：./dev-backend.sh [-f|--force]
#   -f, --force   端口被占用时自动释放
#
# 前置依赖：PostgreSQL 容器 pg17（5433） + Redis 容器 redis（6379）
set -euo pipefail

SCRIPT_NAME="dev-backend"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/_common.sh"

# --- 参数解析 ---
for arg in "$@"; do
  case "$arg" in
    -f|--force) FORCE_RELEASE=true ;;
    *) err "$SCRIPT_NAME" "未知参数: $arg"; exit 1 ;;
  esac
done

# --- 前置检查 ---
command -v docker >/dev/null 2>&1 || { err "$SCRIPT_NAME" "未找到 docker"; exit 1; }
check_container pg17
check_container redis

ensure_local_config

ensure_port_free "$BACKEND_PORT" "后端"

# --- 启动后端 ---
log "$SCRIPT_NAME" "启动后端（Spring Boot, 端口 $BACKEND_PORT）..."
cd "$BACKEND_DIR"
exec ./gradlew :sei-online-code-service:bootRun --args="\
--spring.profiles.active=local \
--spring.config.additional-location=file:${LOCAL_CFG_DIR}/ \
--spring.cloud.nacos.config.enabled=false \
--spring.cloud.nacos.config.import-check.enabled=false \
--spring.cloud.nacos.discovery.enabled=false"
