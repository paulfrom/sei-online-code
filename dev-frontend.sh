#!/usr/bin/env bash
# 本地启动前端（UmiJS dev server）
# 用法：./dev-frontend.sh [-f|--force]
#   -f, --force   端口被占用时自动释放
set -euo pipefail

SCRIPT_NAME="dev-frontend"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/_common.sh"

# --- 参数解析 ---
for arg in "$@"; do
  case "$arg" in
    -f|--force) FORCE_RELEASE=true ;;
    *) err "$SCRIPT_NAME" "未知参数: $arg"; exit 1 ;;
  esac
done

ensure_port_free "$FRONTEND_PORT" "前端"

# --- 启动前端 ---
log "$SCRIPT_NAME" "启动前端（UmiJS dev, 端口 $FRONTEND_PORT）..."
cd "$FRONTEND_DIR"
exec pnpm start
