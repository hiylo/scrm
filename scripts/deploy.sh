#!/usr/bin/env bash
#
# Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
# Project : scrm
# File : deploy.sh
# Description : 构建并部署 scrm-finance, 保证前端产物与源码一致。
#
# 背景: 曾出现「改了前端源码但只跑了 mvn package」的情况 —— 后端 class
# 更新了, 而 src/main/resources/static 仍是上一次 vite build 的旧产物,
# 部署后用真实页面验证得到的是旧界面的结论, 结论作废。
#
# 本脚本做三件事:
#   1. 强制重新 vite build, 并校验 dist 比所有前端源码都新;
#   2. 校验 dist 与 src/main/resources/static 内容一致(按文件清单+大小);
#   3. 打包、重启、等待 health 通过, 失败则非零退出。
#
# 用法: ./scripts/deploy.sh [--skip-tests]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$ROOT/frontend"
STATIC="$ROOT/src/main/resources/static"
HEALTH_URL="http://localhost:8840/actuator/health"
SERVICE="scrm-finance"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m[FAIL] %s\033[0m\n' "$*" >&2; exit 1; }

# ---------- 1. 前端构建 ----------
log "前端构建"
cd "$FRONTEND"
if [[ "${1:-}" != "--skip-tests" ]]; then
  npx tsc --noEmit || die "tsc --noEmit 有类型错误"
fi
npx vite build || die "vite build 失败"

# 校验: dist 中最新的文件必须比 .tsx/.ts 源码最新文件更新
NEWEST_SRC=$(find "$FRONTEND/src" -type f \( -name '*.tsx' -o -name '*.ts' \) -printf '%T@\n' | sort -rn | head -1)
NEWEST_DIST=$(find "$FRONTEND/dist" -type f -printf '%T@\n' | sort -rn | head -1)
if awk "BEGIN{exit !($NEWEST_SRC > $NEWEST_DIST)}"; then
  die "dist 比源码旧, 构建产物与源码不一致"
fi
log "产物新鲜度校验通过"

# ---------- 2. 同步静态资源 ----------
log "同步静态资源到 src/main/resources/static"
rm -rf "$STATIC"
cp -r "$FRONTEND/dist" "$STATIC"

# 校验: 两边文件清单与字节数必须完全一致
DIST_SUM=$(cd "$FRONTEND/dist" && find . -type f -printf '%p %s\n' | sort | sha256sum | cut -d' ' -f1)
STATIC_SUM=$(cd "$STATIC" && find . -type f -printf '%p %s\n' | sort | sha256sum | cut -d' ' -f1)
[[ "$DIST_SUM" == "$STATIC_SUM" ]] || die "静态资源同步不一致"
log "静态资源一致性校验通过"

# ---------- 3. 打包 ----------
log "后端打包"
cd "$ROOT"
rm -rf target/classes/static
mvn -o package -DskipTests -q || die "mvn package 失败"
log "打包完成: $(ls -la target/*.jar | awk '{print $NF, $5}')"

# ---------- 4. 重启并等待健康 ----------
log "重启服务"
sudo systemctl restart "$SERVICE"

log "等待 health 通过 (最多 120s)"
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)
  if [[ "$code" == "200" ]]; then
    log "health:200, 部署完成 (耗时约 $((i*2))s)"
    exit 0
  fi
  sleep 2
done
die "服务启动后 health 未在 120s 内返回 200"
