#!/usr/bin/env bash
# 本地一键启动前端 + 后端（不启用 Nacos，使用本地 PostgreSQL + Redis）。
# 用法：./dev-start.sh   （Ctrl+C 一并停止前后端）
#
# 前置依赖（本脚本只检查、不自动创建）：
#   - PostgreSQL 容器 pg17           监听 5433，已导入 db/migration V1–V5
#   - Redis 容器     sei-local-redis 监听 6379（sei-core 多级缓存强依赖）
# 端口：后端 8091 / 前端 8000
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
LOCAL_CFG_DIR="$BACKEND_DIR/sei-online-code-service/local-config"
LOCAL_CFG_FILE="$LOCAL_CFG_DIR/application-local.yaml"
BACKEND_PORT=8091
FRONTEND_PORT=8000

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
check_container sei-local-redis
log "依赖容器 pg17、sei-local-redis 均在运行。"

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

# --- 端口占用检查 ---
port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
for p in "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if port_busy "$p"; then
    err "端口 $p 已被占用。请释放后重试（后端=$BACKEND_PORT，前端=$FRONTEND_PORT）。"
    exit 1
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
