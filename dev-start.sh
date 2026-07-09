#!/usr/bin/env bash
# 本地一键启动前端 + 后端（不启用 Nacos，使用本地 PostgreSQL + Redis）。
# 用法：./dev-start.sh [-f|--force]
#   -f, --force   端口被占用时自动释放（kill 占用进程），不询问
#   Ctrl+C        一并停止前后端
#
# 前置依赖（本脚本只检查、不自动创建）：
#   - PostgreSQL 容器 pg17           监听 5433，已导入 db/migration V1–V5
#   - Redis 容器     redis           监听 6379（sei-core 多级缓存强依赖，事先常驻运行）
# 端口：后端 8091 / 前端 8000
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
LOCAL_CFG_DIR="$BACKEND_DIR/sei-online-code-service/local-config"
LOCAL_CFG_FILE="$LOCAL_CFG_DIR/application-local.yaml"
BACKEND_PORT=8091
FRONTEND_PORT=8000
FORCE_RELEASE=false

# --- 参数解析 ---
for arg in "$@"; do
  case "$arg" in
    -f|--force) FORCE_RELEASE=true ;;
    *) err "未知参数: $arg"; exit 1 ;;
  esac
done

log() { printf '\033[36m[dev-start]\033[0m %s\n' "$*"; }
err() { printf '\033[31m[dev-start]\033[0m %s\n' "$*" >&2; }

# --- 前置检查：依赖容器 ---
check_container() {
  local name="$1"
  if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
    err "依赖容器 '$name' 未运行。请先启动它（见脚本头部说明）后重试。"
    exit 1
  fi
}
command -v docker >/dev/null 2>&1 || { err "未找到 docker，无法校验 pg17 / redis 依赖。"; exit 1; }
check_container pg17
check_container redis
log "依赖容器 pg17、redis 均在运行。"

# --- 本地配置：缺失时自动生成模板 ---
if [ ! -f "$LOCAL_CFG_FILE" ]; then
  log "未找到 $LOCAL_CFG_FILE，正在生成模板（密码请自行修改）..."
  mkdir -p "$LOCAL_CFG_DIR"
  cat > "$LOCAL_CFG_FILE" <<- 'YAML'
# 本地启动专用配置 — 模板文件
# 首次拉取仓库后执行以下步骤：
#   1. 填入 spring.datasource.password 为你的本地 PostgreSQL 密码
#   2. cd backend && ./gradlew :sei-online-code-service:bootRun --args="--spring.profiles.active=local ..."  # 或用 ./dev-start.sh
spring:
  cloud:
    nacos:
      config:
        enabled: false
        import-check:
          enabled: false
      discovery:
        enabled: false
    service-registry:
      auto-registration:
        enabled: false
  datasource:
    url: jdbc:postgresql://localhost:5433/postgres
    username: postgres
    # password: 请替换为你的本地 PostgreSQL 密码
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: sei-online-code-local
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false
    show-sql: false
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3000ms
  autoconfigure:
    exclude:
      - com.changhong.sei.config.autoconfigure.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration
      - com.changhong.sei.core.mq.config.KafkaConsumerConfig
      - com.changhong.sei.core.mq.config.KafkaProducerConfig
server:
  port: 8091
YAML
  log "模板已生成，请编辑 $LOCAL_CFG_FILE 填入密码后重新运行。"
  exit 1
fi

# --- 端口占用检测 ---
# 使用 ss（优先）或 lsof（备选）检测端口占用。
# 返回值：0=被占用, 1=空闲。
check_port() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tlnpH "sport = :$port" 2>/dev/null | grep -q ":$port" && return 0 || return 1
  elif command -v lsof >/dev/null 2>&1; then
    lsof -i :"$port" -sTCP:LISTEN 2>/dev/null | grep -q . && return 0 || return 1
  else
    # fallback: bash /dev/tcp（可能不可用）
    (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null && return 0 || return 1
  fi
}

# 获取占用指定端口的进程信息（PID + 命令）。
# 输出格式："PID COMMAND"；无占用则输出空。
port_pid_info() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tlnpH "sport = :$port" 2>/dev/null | awk -v p="$port" '$1 ~ /LISTEN/ {split($NF,a,":"); if(a[2]==p) {g(/.*pid=/, "", $6); split($6,a2,","); print a2[1], $7}}'
  elif command -v lsof >/dev/null 2>&1; then
    lsof -i :"$port" -sTCP:LISTEN -o 2>/dev/null | awk 'NR>1{print $2, $1}'
  fi
}

# --- 端口冲突处理 ---
release_port() {
  local port="$1"
  local label="$2"
  local pid cmd
  if command -v lsof >/dev/null 2>&1; then
    pid=$(lsof -ti :"$port" -sTCP:LISTEN 2>/dev/null)
  elif command -v ss >/dev/null 2>&1; then
    pid=$(ss -tlnpH "sport = :$port" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -1)
  fi
  if [ -z "${pid:-}" ]; then
    err "端口 $port 已被占用，但无法定位占用进程，请手动释放。"
    return 1
  fi
  cmd="$([ -r /proc/$pid/comm ] && cat /proc/$pid/comm 2>/dev/null || echo "PID:$pid")"
  if [ "$FORCE_RELEASE" = true ]; then
    log "端口 $port（$label）被进程 $pid（$cmd）占用，正在强制释放（--force）..."
    kill "$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
    sleep 1
  else
    err "端口 $port（$label）已被进程 $pid（$cmd）占用。"
    printf '\033[33m[dev-start]\033[0m 是否终止该进程释放端口？[y/N] ' >&2
    read -r answer
    case "$answer" in
      y|Y|yes|YES)
        kill "$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
        sleep 1
        if check_port "$port"; then
          err "释放端口 $port 失败，请手动处理。"
          return 1
        fi
        log "端口 $port 已释放。"
        ;;
      *)
        err "请手动释放端口 $port 后重试。"
        return 1
        ;;
    esac
  fi
  # 确认释放成功
  if check_port "$port"; then
    err "端口 $port 释放失败，请手动处理。"
    return 1
  fi
  log "端口 $port（$label）已释放。"
}

for entry in "$BACKEND_PORT:后端" "$FRONTEND_PORT:前端"; do
  port="${entry%%:*}"
  label="${entry#*:}"
  if check_port "$port"; then
    release_port "$port" "$label" || exit 1
  fi
done

# --- 统一收尾：Ctrl+C 或退出时结束子进程 ---
PIDS=()
cleanup() {
  log "正在停止前后端 ..."
  for pid in "${PIDS[@]:-}"; do
    [ -n "${pid:-}" ] && kill "$pid" 2>/dev/null || true
  done
  # 结束 gradle 派生的 bootRun JVM
  pkill -f "com.changhong.onlinecode.RestApplication" 2>/dev/null || true
  wait 2>/dev/null || true
  log "已停止。"
}
trap cleanup INT TERM EXIT

# --- 启动后端 ---
log "启动后端（Spring Boot, 端口 $BACKEND_PORT）..."
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
log "启动前端（UmiJS dev, 端口 $FRONTEND_PORT）..."
(
  cd "$FRONTEND_DIR"
  MOCK=none pnpm start 2>&1 | sed 's/^/[frontend] /'
) &
PIDS+=("$!")

log "前端 http://localhost:$FRONTEND_PORT  ←代理→  后端 http://localhost:$BACKEND_PORT"
log "按 Ctrl+C 一并停止。"

# 任一子进程退出即触发整体收尾
wait -n
