#!/usr/bin/env bash
# 一键启动前端 + 后端（不启用 Nacos，使用本地 PostgreSQL + Redis）。
# 用法：./dev-start.sh [-f|--force]
#   -f, --force   端口被占用时自动释放（kill 占用进程），不询问
#   Ctrl+C        一并停止前后端
#
# 前置依赖：
#   - PostgreSQL 容器 pg17  监听 5433
#   - Redis 容器     redis  监听 6379
# 端口：后端 8091 / 前端 8000
set -euo pipefail

SCRIPT_NAME="dev-start"
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
log "$SCRIPT_NAME" "依赖容器 pg17、redis 均在运行。"

ensure_local_config

for entry in "$BACKEND_PORT:后端" "$FRONTEND_PORT:前端"; do
  port="${entry%%:*}"
  label="${entry#*:}"
  ensure_port_free "$port" "$label"
done

# --- 统一收尾：Ctrl+C 或退出时结束子进程 ---
PIDS=()
cleanup() {
  log "$SCRIPT_NAME" "正在停止前后端 ..."
  for pid in "${PIDS[@]:-}"; do
    [ -n "${pid:-}" ] && kill "$pid" 2>/dev/null || true
  done
  pkill -f "com.changhong.onlinecode.RestApplication" 2>/dev/null || true
  wait 2>/dev/null || true
  log "$SCRIPT_NAME" "已停止。"
}
trap cleanup INT TERM EXIT

# --- 启动后端 ---
log "$SCRIPT_NAME" "启动后端（Spring Boot, 端口 $BACKEND_PORT）..."
(
  cd "$BACKEND_DIR"
  ./gradlew :sei-online-code-service:bootRun --args="\
--spring.profiles.active=local \
--spring.config.additional-location=file:${LOCAL_CFG_DIR}/ \
--spring.cloud.nacos.config.enabled=false \
--spring.cloud.nacos.config.import-check.enabled=false \
--spring.cloud.nacos.discovery.enabled=false" 2>&1 | sed 's/^/[backend] /'
) &
PIDS+=("$!")

# --- 启动前端 ---
log "$SCRIPT_NAME" "启动前端（UmiJS dev, 端口 $FRONTEND_PORT）..."
(
  cd "$FRONTEND_DIR"
  MOCK=none pnpm start 2>&1 | sed 's/^/[frontend] /'
) &
PIDS+=("$!")

log "$SCRIPT_NAME" "前端 http://localhost:$FRONTEND_PORT  ←代理→  后端 http://localhost:$BACKEND_PORT"
log "$SCRIPT_NAME" "按 Ctrl+C 一并停止。"

# 任一子进程退出即触发整体收尾
wait -n
