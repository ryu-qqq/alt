#!/usr/bin/env bash
# alt — 평가용 검증 스크립트
#
# 전제: docker compose up --build 로 앱이 떠 있어야 한다.
# 사용: ./scripts/demo.sh
#
# 모든 시나리오를 자동 실행하고 PASS/FAIL 을 콘솔에 찍는다.
# 종료 코드 0 = 전체 PASS, 1 = 실패가 하나라도 있음.

set -uo pipefail

BASE="http://localhost:8080"
PASS=0; FAIL=0

# ── 색상 ────────────────────────────────────────────────
if [[ -t 1 ]]; then
  G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; D=$'\e[2m'; N=$'\e[0m'
else
  G=""; R=""; Y=""; D=""; N=""
fi

# ── 사전 확인 ────────────────────────────────────────────
echo "${Y}== Health check ==${N}"
HEALTH=$(curl -sf "$BASE/actuator/health" 2>/dev/null || true)
if [[ "$HEALTH" == *'"status":"UP"'* ]]; then
  echo "${G}✓ UP${N}"
else
  echo "${R}✗ FAIL — 앱이 안 떠있다. 'docker compose up --build' 후 다시 실행${N}"
  exit 1
fi
echo

# ── 헬퍼 ────────────────────────────────────────────────
# usage: assert_status <expected> <actual> <label>
assert_status() {
  local exp="$1" act="$2" label="$3"
  if [[ "$exp" == "$act" ]]; then
    echo "${G}✓ PASS${N} ${label}  ${D}(HTTP $act)${N}"
    PASS=$((PASS+1))
  else
    echo "${R}✗ FAIL${N} ${label}  ${D}(expected HTTP $exp, got $act)${N}"
    FAIL=$((FAIL+1))
  fi
}

# usage: assert_contains <needle> <haystack> <label>
assert_contains() {
  local needle="$1" hay="$2" label="$3"
  if [[ "$hay" == *"$needle"* ]]; then
    echo "${G}✓ PASS${N} ${label}  ${D}(found '$needle')${N}"
    PASS=$((PASS+1))
  else
    echo "${R}✗ FAIL${N} ${label}  ${D}(missing '$needle' in: $hay)${N}"
    FAIL=$((FAIL+1))
  fi
}

# ── 시나리오 1: 시드 회원 이력 조회 ──────────────────────
echo "${Y}== 1. 시드 회원 이력 조회 (010-1234-5678) ==${N}"
RESP=$(curl -sf "$BASE/api/v1/subscriptions/history?phoneNumber=01012345678" || echo '{}')
assert_contains '"channelName":"이메일"'   "$RESP" "이력에 이메일 채널 포함"
assert_contains '"channelName":"홈페이지"' "$RESP" "이력에 홈페이지 채널 포함"
assert_contains '"summary"'                "$RESP" "summary 필드 존재 (null 가능)"
# COMMITTED 4건만 노출되는지 (ROLLED_BACK 제외)
COUNT=$(echo "$RESP" | grep -o '"attemptId"' | wc -l | tr -d ' ')
[[ "$COUNT" == "4" ]] && { echo "${G}✓ PASS${N} COMMITTED 4건만 노출"; PASS=$((PASS+1)); } || { echo "${R}✗ FAIL${N} attemptId 개수 expected 4, got $COUNT"; FAIL=$((FAIL+1)); }
echo

# ── 시나리오 2: 멱등성 ───────────────────────────────────
echo "${Y}== 2. 멱등성 (같은 Idempotency-Key 두 번) ==${N}"
KEY=$(uuidgen)
PHONE="01077$(printf %06d $RANDOM)"
ST1=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/subscriptions/subscribe" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d "{\"phoneNumber\":\"$PHONE\",\"channelId\":1,\"targetStatus\":\"BASIC\"}")
assert_status 200 "$ST1" "1차 요청 정상 처리"

R2=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/v1/subscriptions/subscribe" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d "{\"phoneNumber\":\"$PHONE\",\"channelId\":1,\"targetStatus\":\"BASIC\"}")
ST2=$(echo "$R2" | tail -1)
B2=$(echo "$R2" | sed '$d')
assert_status 409 "$ST2" "2차 같은 키 거절 (HTTP 409)"
assert_contains '"code":"SUB-201"' "$B2" "에러 코드 SUB-201 (IDEMPOTENCY_CONFLICT)"
echo

# ── 시나리오 3: Idempotency-Key 누락 ─────────────────────
echo "${Y}== 3. Idempotency-Key 누락 → 400 ==${N}"
R3=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/v1/subscriptions/subscribe" \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01088886666","channelId":1,"targetStatus":"BASIC"}')
ST3=$(echo "$R3" | tail -1); B3=$(echo "$R3" | sed '$d')
assert_status 400 "$ST3" "헤더 누락 거절"
assert_contains '"code":"MISSING_HEADER"' "$B3" "에러 코드 MISSING_HEADER"
echo

# ── 시나리오 4: 채널 권한 ────────────────────────────────
echo "${Y}== 4. 채널 권한 — 콜센터(UNSUBSCRIBE_ONLY)로 구독 시도 ==${N}"
R4=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/v1/subscriptions/subscribe" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"01055554444","channelId":5,"targetStatus":"BASIC"}')
ST4=$(echo "$R4" | tail -1); B4=$(echo "$R4" | sed '$d')
assert_status 403 "$ST4" "구독 거절"
assert_contains '"code":"CHN-002"' "$B4" "에러 코드 CHN-002"
echo

# ── 시나리오 5: 잘못된 도메인 전이 ───────────────────────
echo "${Y}== 5. 잘못된 도메인 전이 — NONE 회원 해지 ==${N}"
R5=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/v1/subscriptions/unsubscribe" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"01012345678","channelId":5,"targetStatus":"BASIC"}')
ST5=$(echo "$R5" | tail -1); B5=$(echo "$R5" | sed '$d')
assert_status 403 "$ST5" "전이 거절"
assert_contains '"code":"SUB-002"' "$B5" "에러 코드 SUB-002"
echo

# ── 시나리오 6: 회원 미존재 ──────────────────────────────
echo "${Y}== 6. 회원 미존재 unsubscribe → 404 ==${N}"
R6=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/v1/subscriptions/unsubscribe" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"01099990000","channelId":5,"targetStatus":"NONE"}')
ST6=$(echo "$R6" | tail -1); B6=$(echo "$R6" | sed '$d')
assert_status 404 "$ST6" "회원 미존재"
assert_contains '"code":"MEM-001"' "$B6" "에러 코드 MEM-001"
echo

# ── 시나리오 7: 잘못된 휴대폰 ────────────────────────────
echo "${Y}== 7. 잘못된 휴대폰 → 400 ==${N}"
R7=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/v1/subscriptions/subscribe" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"abc","channelId":1,"targetStatus":"BASIC"}')
ST7=$(echo "$R7" | tail -1); B7=$(echo "$R7" | sed '$d')
assert_status 400 "$ST7" "휴대폰 형식 오류"
assert_contains '"code":"MEM-002"' "$B7" "에러 코드 MEM-002"
echo

# ── 결과 ────────────────────────────────────────────────
echo "${Y}========================================${N}"
TOTAL=$((PASS+FAIL))
if [[ $FAIL -eq 0 ]]; then
  echo "${G}ALL PASS — $PASS / $TOTAL${N}"
  exit 0
else
  echo "${R}FAIL — $FAIL / $TOTAL${N}"
  exit 1
fi
