#!/usr/bin/env bash
#
# SCRM 销售外呼域一键联调脚本（后端 HTTP 全链路）
#
# 用途:
#   在开发机一次性验证销售外呼域（/scrm/sales-app/**）的完整后端链路:
#     Flyway 迁移 → 登录 → /leads → /calls/batch 幂等 → /phone-lookup
#     → 保存联系方式 → /recordings 上传(对象存储) → /recordings/coverage → /capability
#
# 环境隔离:
#   - 独立 PostgreSQL 容器 (scrm-it-pg, 端口 54329), 不碰开发库
#   - 独立后端端口 8841, 不碰 8840
#   - 对象存储: NAS MinIO (192.168.1.150:9000), 独立桶 scrm-it
#   - 全部资源脚本退出时可清理 (--keep 保留)
#
# 用法:
#   bash scripts/sales-app-smoke.sh [--keep]
#
# 依赖: docker / curl / python3 / 已 build 的 target/scrm-server-1.0.0.jar
#       (未 build 时自动 mvn -o -q package -DskipTests)
#
# 退出码: 0=全绿 1=失败 2=环境缺失
set -uo pipefail

# 仓库根: 由脚本位置推导，避免硬编码 /workspaces/scrm
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 2

ART_BASE="${SCRM_ART_BASE:-/opt/test-artifacts/scrm}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ART_BASE/smoke/sales-app/$STAMP"
LOG="$ART_BASE/logs/sales-app-smoke-$STAMP.log"
mkdir -p "$OUT" "$ART_BASE/logs"

PG_CTNR="scrm-it-pg"
PG_PORT="${SCRM_IT_PG_PORT:-54329}"
SRV_PORT="${SCRM_IT_SRV_PORT:-8841}"
JWT_SECRET="scrm-it-key-$(openssl rand -hex 12)"
MINIO_BUCKET="scrm-it"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-192.168.1.150:9000}"
# MinIO 凭据：优先用环境变量；缺省时从 framework/vision-service/.env 读取（该文件存 NAS MinIO 真实 AK/SK，
# 属于既有基础设施配置，见 memory.md 基础设施段；本脚本不回显凭据、不写入仓库）
if [ -z "${MINIO_ACCESS_KEY:-}" ] || [ -z "${MINIO_SECRET_KEY:-}" ]; then
  ENV_FILE="$(dirname "$REPO_ROOT")/framework/vision-service/.env"
  if [ -f "$ENV_FILE" ]; then
    while IFS= read -r line; do
      case "$line" in
        MINIO_ACCESS_KEY=*|MINIO_SECRET_KEY=*) eval "export $line" ;;
      esac
    done < "$ENV_FILE"
  fi
fi
if [ -z "${MINIO_ACCESS_KEY:-}" ] || [ -z "${MINIO_SECRET_KEY:-}" ]; then
  echo "错误: 未提供 MINIO_ACCESS_KEY/MINIO_SECRET_KEY（可设置环境变量，或确保 framework/vision-service/.env 存在）" >&2
  exit 2
fi
echo "MinIO 凭据: endpoint=$MINIO_ENDPOINT bucket=$MINIO_BUCKET (AK/SK 长度 ${#MINIO_ACCESS_KEY}/${#MINIO_SECRET_KEY})"

# ---------- 辅助 ----------
say()  { printf '\n== %s ==\n' "$*"; }
fail() { say "❌ $*"; echo "LOGS: $LOG"; exit "${2:-1}"; }
pyget() { python3 -c "import json,sys;d=json.load(sys.stdin);print(d$1)" 2>/dev/null; }

# 参数解析: --keep 保留测试资源（PG 容器 + 后端进程），便于人工复查
KEEP=0
for arg in "$@"; do
  case "$arg" in
    --keep) KEEP=1 ;;
    -h|--help) sed -n '1,25p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg (支持 --keep)" >&2; exit 2 ;;
  esac
done

cleanup() {
  [ "$KEEP" = "1" ] && return
  say "清理测试资源"
  [ -n "${SRV_PID:-}" ] && kill "$SRV_PID" 2>/dev/null
  docker rm -f "$PG_CTNR" >/dev/null 2>&1
}
trap cleanup EXIT

# ---------- 0. 前置检查 ----------
for c in docker curl python3 openssl; do command -v "$c" >/dev/null || fail "缺少命令 $c" 2; done
command -v mvn >/dev/null || fail "缺少 mvn" 2

# Java 可执行文件: 默认 dragonwell-21，可用 JAVA_BIN 覆盖
JAVA_BIN="${JAVA_BIN:-/usr/lib/jvm/dragonwell-21/bin/java}"
[ -x "$JAVA_BIN" ] || JAVA_BIN="$(command -v java || true)"
[ -x "$JAVA_BIN" ] || fail "未找到可用的 java（可用 JAVA_BIN 指定）" 2

# ---------- 1. 构建（如 jar 缺失或早于源码） ----------
JAR="$REPO_ROOT/target/scrm-server-1.0.0.jar"
if [ ! -f "$JAR" ] || [ src/main/java/org/hiylo/scrm/service/ScrmSalesAppService.java -nt "$JAR" ]; then
  say "重新构建后端 jar (mvn -o package)"
  mvn -o -q package -DskipTests || fail "构建失败"
fi

# ---------- 2. 起独立 PostgreSQL ----------
say "启动独立 PG 容器 ($PG_CTNR :$PG_PORT)"
docker rm -f "$PG_CTNR" >/dev/null 2>&1
docker run -d --name "$PG_CTNR" \
  -e POSTGRES_DB=scrm -e POSTGRES_USER=scrm -e POSTGRES_PASSWORD=scrm-it-password -e TZ=Asia/Shanghai \
  -p "$PG_PORT:5432" postgres:16-alpine >/dev/null || fail "PG 容器启动失败"
for i in $(seq 1 30); do
  docker exec "$PG_CTNR" pg_isready -U scrm -d scrm >/dev/null 2>&1 && break
  [ "$i" = 30 ] && fail "PG 未就绪"
  sleep 1
done

# ---------- 3. 启动后端（独立端口 + NAS MinIO 独立桶） ----------
say "启动后端 :$SRV_PORT (PG :$PG_PORT, MinIO $MINIO_ENDPOINT/$MINIO_BUCKET)"
PGPW="scrm-it-password" \
SPRING_PROFILES_ACTIVE=dev \
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/scrm?currentSchema=scrm" \
SPRING_DATASOURCE_USERNAME=scrm SPRING_DATASOURCE_PASSWORD="$PGPW" \
SCRM_JWT_SECRET="$JWT_SECRET" \
SERVER_PORT="$SRV_PORT" \
SCRM_STORAGE_ENDPOINT="http://$MINIO_ENDPOINT" \
SCRM_STORAGE_ACCESS_KEY="$MINIO_ACCESS_KEY" \
SCRM_STORAGE_SECRET_KEY="$MINIO_SECRET_KEY" \
SCRM_STORAGE_BUCKET="$MINIO_BUCKET" \
setsid "$JAVA_BIN" -jar "$JAR" > "$LOG" 2>&1 < /dev/null &
SRV_PID=$!
for i in $(seq 1 90); do
  BODY="$(curl -s -m 2 "http://127.0.0.1:$SRV_PORT/actuator/health" 2>/dev/null)"
  echo "$BODY" | grep -q '"status":"UP"' && break
  [ "$i" = 90 ] && fail "后端未就绪 (见 $LOG)"
  sleep 1
done
B="http://127.0.0.1:$SRV_PORT"
grep -E "MinIO 存储就绪|MinIO 存储不可用" "$LOG" | head -2 >> "$OUT/startup.txt"

# ---------- 4. 登录 ----------
say "登录 (bootstrap admin)"
LOGIN="$(curl -s -X POST "$B/scrm/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123456"}')"
echo "$LOGIN" > "$OUT/login.json"
TOKEN="$(echo "$LOGIN" | pyget "['data']['accessToken']")"
MYUID="$(echo "$LOGIN" | pyget "['data']['userId']")"
[ -z "$TOKEN" ] && fail "登录失败"
AUTH="Authorization: Bearer $TOKEN"
echo "uid=$MYUID roles=$(echo "$LOGIN" | pyget "['data']['roles']")"

# ---------- 5. 种子数据（直接 SQL 插入，绕过公海创建接口） ----------
say "种子数据: 2 条公海线索 + 联系方式"
cat > "$OUT/seed.sql" <<SQL
SET search_path TO scrm;
INSERT INTO scrm_public_sea_customer
  (id, platform_type, platform_customer_uid, nickname, lifecycle, remark,
   assigned_to, assigned_at, status, create_time, update_time, version)
VALUES
  (900000000000000101, 'wechat_personal', 'smoke_lead_101', '联调线索张三', 'NEW', 'smoke',
   '$MYUID', now(), 'ASSIGNED', now(), now(), 0),
  (900000000000000102, 'wechat_personal', 'smoke_lead_102', '联调线索李四', 'PROSPECT', 'smoke',
   '$MYUID', now(), 'ASSIGNED', now(), now(), 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO scrm_customer_contact
  (id, owner_type, customer_id, phone, phone_type, is_primary, create_time, update_time, version)
VALUES
  (900000000000000111, 'PUBLIC_SEA', 900000000000000101, '13800138000', 'MOBILE', true, now(), now(), 0),
  (900000000000000112, 'PUBLIC_SEA', 900000000000000102, '13900139000', 'MOBILE', true, now(), now(), 0)
ON CONFLICT (id) DO NOTHING;
SQL
docker exec -i "$PG_CTNR" psql -U scrm -d scrm < "$OUT/seed.sql" >/dev/null || fail "种子数据失败"

# ---------- 6. /leads ----------
say "/leads"
LEADS="$(curl -s "$B/scrm/sales-app/leads?page=0&size=20" -H "$AUTH")"
echo "$LEADS" > "$OUT/leads.json"
LEAD_N="$(echo "$LEADS" | python3 -c "import json,sys;d=json.load(sys.stdin);print(len(d['data']['content']))" 2>/dev/null)"
[ "$LEAD_N" = "2" ] || fail "leads 数量异常: $LEAD_N"
echo "items=$LEAD_N ✅"

# ---------- 7. /calls/batch 幂等 ----------
say "/calls/batch (首次 + 重放幂等)"
REPORT='[{"deviceId":"smoke-dev","direction":"OUT","peerNumber":"13800138000","startAt":"2026-10-22 12:00:00","durationSec":95,"connected":true,"source":"APP_CALL","customerId":900000000000000101,"customerType":"PUBLIC_SEA"}]'
R1="$(curl -s -X POST "$B/scrm/sales-app/calls/batch" -H "$AUTH" -H 'Content-Type: application/json' -d "$REPORT")"
echo "$R1" > "$OUT/calls-first.json"
R2="$(curl -s -X POST "$B/scrm/sales-app/calls/batch" -H "$AUTH" -H 'Content-Type: application/json' -d "$REPORT")"
echo "$R2" > "$OUT/calls-dup.json"
C1="$(echo "$R1" | pyget "['data']['created']")"; D2="$(echo "$R2" | pyget "['data']['duplicated']")"
[ "$C1" = "1" ] && [ "$D2" = "1" ] || fail "幂等异常 created=$C1 dup=$D2"
echo "first.created=$C1 replay.duplicated=$D2 ✅"

# ---------- 8. /phone-lookup ----------
say "/phone-lookup"
PL="$(curl -s "$B/scrm/sales-app/phone-lookup?phone=13800138000" -H "$AUTH")"
echo "$PL" > "$OUT/phone-lookup.json"
PT="$(echo "$PL" | pyget "['data'][0]['ownerType']")"; PI="$(echo "$PL" | pyget "['data'][0]['ownerId']")"
[ "$PT" = "PUBLIC_SEA" ] && [ "$PI" = "900000000000000101" ] || fail "归属匹配异常"
echo "13800138000 → $PT $PI ✅"

# ---------- 9. 保存联系方式（主号码切换） ----------
say "保存联系方式 (主号码切换)"
CS="$(curl -s -X POST "$B/scrm/sales-app/owners/PUBLIC_SEA/900000000000000102/contacts" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"phone":"13700137000","phoneType":"MOBILE","isPrimary":true}')"
echo "$CS" > "$OUT/contact-save.json"
CP="$(echo "$CS" | pyget "['data']['phone']")"; CPR="$(echo "$CS" | pyget "['data']['isPrimary']")"
[ "$CP" = "13700137000" ] && [ "$CPR" = "True" ] || fail "保存联系方式异常"
echo "saved=$CP primary=$CPR ✅"

# ---------- 10. /recordings 上传（NAS MinIO 独立桶） ----------
say "/recordings 上传 (NAS MinIO $MINIO_BUCKET)"
WAV="$OUT/demo.wav"
python3 - "$WAV" <<'PY'
import sys, wave, struct, math
with wave.open(sys.argv[1], 'w') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(8000)
    w.writeframes(b''.join(struct.pack('<h', int(6000*math.sin(2*math.pi*440*i/8000))) for i in range(8000)))
PY
CALL_ID="$(docker exec "$PG_CTNR" psql -U scrm -d scrm -tAc \
  "select id from scrm.scrm_call_record order by id limit 1")"
UP="$(curl -s -X POST "$B/scrm/sales-app/recordings" -H "$AUTH" \
  -F "deviceId=smoke-dev" -F "source=OEM_RECORD" \
  -F "callRecordId=$CALL_ID" -F "ownerType=PUBLIC_SEA" -F "ownerId=900000000000000101" \
  -F "durationSec=85" -F "file=@$WAV;type=audio/wav")"
echo "$UP" > "$OUT/recording-upload.json"
UURL="$(echo "$UP" | pyget "['data']['url']")"; UOK="$(echo "$UP" | pyget "['code']")"
[ "$UOK" = "200" ] && [ -n "$UURL" ] || fail "录音上传失败"
# 预签名 URL 字节级校验
curl -s -o "$OUT/downloaded.wav" -w "download_http=%{http_code} size=%{size_download}\n" "$UURL" > "$OUT/dl-status.txt"
cmp -s "$WAV" "$OUT/downloaded.wav" || fail "预签名下载内容不一致"
# 重复上传幂等
UP2="$(curl -s -X POST "$B/scrm/sales-app/recordings" -H "$AUTH" \
  -F "deviceId=smoke-dev" -F "source=OEM_RECORD" -F "file=@$WAV;type=audio/wav")"
DUP="$(echo "$UP2" | pyget "['data']['duplicated']")"
[ "$DUP" = "True" ] || fail "重复上传未幂等"
echo "upload ok, url len=${#UURL}, dup=$DUP ✅"

# ---------- 11. /coverage ----------
say "/recordings/coverage"
CV="$(curl -s "$B/scrm/sales-app/recordings/coverage?userId=$MYUID" -H "$AUTH")"
echo "$CV" > "$OUT/coverage.json"
RC="$(echo "$CV" | pyget "['data']['totalRecordings']")"
[ "$RC" = "1" ] || fail "覆盖率异常 recs=$RC"
echo "calls=$(echo "$CV" | pyget "['data']['totalCalls']") recs=$RC rate=$(echo "$CV" | pyget "['data']['coverageRate']") ✅"

# ---------- 12. /capability ----------
say "/recordings/capability"
CA="$(curl -s "$B/scrm/sales-app/recordings/capability?romHint=Xiaomi-HyperOS" -H "$AUTH")"
echo "$CA" > "$OUT/capability.json"
ROM="$(echo "$CA" | pyget "['data']['rom']")"; SUP="$(echo "$CA" | pyget "['data']['supportedByDefault']")"
[ "$ROM" = "MIUI" ] && [ "$SUP" = "True" ] || fail "能力指引异常"
echo "rom=$ROM supported=$SUP ✅"

# ---------- 汇总 ----------
say "✅ 全部通过 (12/12)"
echo "ARTIFACTS: $OUT"
echo "LOG: $LOG"
exit 0
