#!/usr/bin/env bash
# dev-start 通用工具函数与常量
# 被 dev-start.sh / dev-backend.sh / dev-frontend.sh source
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
LOCAL_CFG_DIR="$BACKEND_DIR/sei-online-code-service/local-config"
LOCAL_CFG_FILE="$LOCAL_CFG_DIR/application-local.yaml"
BACKEND_PORT=8091
FRONTEND_PORT=8000
FORCE_RELEASE=false

log() { printf '\033[36m[%s]\033[0m %s\n' "${1:-dev-start}" "$2"; }
err()  { printf '\033[31m[%s]\033[0m %s\n' "${1:-dev-start}" "$2" >&2; }

# --- 依赖容器检查 ---
check_container() {
  local name="$1"
  if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
    err "${SCRIPT_NAME:-dev-start}" "依赖容器 '$name' 未运行。请先启动它后重试。"
    exit 1
  fi
}

# --- 本地配置检查 + 自动生成模板 ---
ensure_local_config() {
  if [ ! -f "$LOCAL_CFG_FILE" ]; then
    log "${SCRIPT_NAME:-dev-start}" "未找到 $LOCAL_CFG_FILE，正在生成模板..."
    mkdir -p "$LOCAL_CFG_DIR"
    cat > "$LOCAL_CFG_FILE" <<- 'YAML'
# 本地启动专用配置 — 模板文件
# 首次拉取仓库后请编辑此文件，填入 spring.datasource.password
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
  address: 0.0.0.0
YAML
    err "${SCRIPT_NAME:-dev-start}" "模板已生成，请编辑 $LOCAL_CFG_FILE 填入密码后重新运行。"
    exit 1
  fi
}

# --- 端口占用检测 ---
check_port() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tlnpH "sport = :$port" 2>/dev/null | grep -q ":$port" && return 0 || return 1
  elif command -v lsof >/dev/null 2>&1; then
    lsof -i :"$port" -sTCP:LISTEN 2>/dev/null | grep -q . && return 0 || return 1
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null && return 0 || return 1
  fi
}

# --- 获取占用端口的进程 PID ---
port_pid() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti :"$port" -sTCP:LISTEN 2>/dev/null
  elif command -v ss >/dev/null 2>&1; then
    ss -tlnpH "sport = :$port" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -1
  fi
}

# --- 端口冲突处理 ---
release_port() {
  local port="$1"
  local label="$2"
  local pid cmd
  pid="$(port_pid "$port")"
  if [ -z "${pid:-}" ]; then
    err "${SCRIPT_NAME:-dev-start}" "端口 $port 已被占用，但无法定位占用进程，请手动释放。"
    return 1
  fi
  cmd="$([ -r /proc/$pid/comm ] && cat /proc/$pid/comm 2>/dev/null || echo "PID:$pid")"
  if [ "$FORCE_RELEASE" = true ]; then
    log "${SCRIPT_NAME:-dev-start}" "端口 $port（$label）被进程 $pid（$cmd）占用，正在强制释放（--force）..."
    kill "$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
    sleep 1
  else
    err "${SCRIPT_NAME:-dev-start}" "端口 $port（$label）已被进程 $pid（$cmd）占用。"
    printf '\033[33m[%s]\033[0m 是否终止该进程释放端口？[y/N] ' "${SCRIPT_NAME:-dev-start}" >&2
    read -r answer
    case "$answer" in
      y|Y|yes|YES)
        kill "$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
        sleep 1
        ;;
      *)
        err "${SCRIPT_NAME:-dev-start}" "请手动释放端口 $port 后重试。"
        return 1
        ;;
    esac
  fi
  if check_port "$port"; then
    err "${SCRIPT_NAME:-dev-start}" "端口 $port 释放失败，请手动处理。"
    return 1
  fi
  log "${SCRIPT_NAME:-dev-start}" "端口 $port（$label）已释放。"
}

# --- 确保端口可用 ---
ensure_port_free() {
  local port="$1"
  local label="$2"
  if check_port "$port"; then
    release_port "$port" "$label" || exit 1
  fi
}
