-- Demo Seed (docker-compose 환경 전용)
--
-- 한 회원의 다양한 구독/해지 이력을 미리 쌓아둔다.
-- → GET /api/v1/subscriptions/history?phoneNumber=01012345678 호출 시 LLM 요약을 즉시 검증 가능.
--
-- 채널은 V20260510__create_initial_schema.sql 에서 시드된 6건을 사용:
--   1=홈페이지(BOTH), 2=모바일앱(BOTH), 3=네이버(SUBSCRIBE_ONLY),
--   4=SKT(SUBSCRIBE_ONLY), 5=콜센터(UNSUBSCRIBE_ONLY), 6=이메일(UNSUBSCRIBE_ONLY)
--
-- 시나리오 (회원 010-1234-5678):
--   - 2026-01-01 09:00  홈페이지로 BASIC 가입         → COMMITTED
--   - 2026-02-01 10:30  모바일앱에서 PREMIUM 업그레이드 → COMMITTED
--   - 2026-03-01 14:00  콜센터로 BASIC 해지 시도       → ROLLED_BACK (csrng=0)
--   - 2026-03-02 14:00  콜센터로 BASIC 해지 재시도     → COMMITTED
--   - 2026-04-01 10:00  이메일로 NONE 해지            → COMMITTED
-- 회원 최종 상태: NONE
-- 이력 조회는 COMMITTED 4건만 노출 (ROLLED_BACK 은 운영용)

INSERT INTO member (phone_number, status, created_at, updated_at)
VALUES ('01012345678', 'NONE', '2026-01-01 09:00:00.000000', '2026-04-01 10:00:01.000000');

SET @member_id = LAST_INSERT_ID();

INSERT INTO subscription_attempt
    (member_id, channel_id, kind, from_status, to_status,
     requested_at, completed_at, status, idempotency_key)
VALUES
    (@member_id, 1, 'SUBSCRIBE',   'NONE',    'BASIC',
     '2026-01-01 09:00:00.000000', '2026-01-01 09:00:01.000000', 'COMMITTED',   'demo-seed-001'),
    (@member_id, 2, 'SUBSCRIBE',   'BASIC',   'PREMIUM',
     '2026-02-01 10:30:00.000000', '2026-02-01 10:30:01.000000', 'COMMITTED',   'demo-seed-002'),
    (@member_id, 5, 'UNSUBSCRIBE', 'PREMIUM', 'BASIC',
     '2026-03-01 14:00:00.000000', '2026-03-01 14:00:01.000000', 'ROLLED_BACK', 'demo-seed-003'),
    (@member_id, 5, 'UNSUBSCRIBE', 'PREMIUM', 'BASIC',
     '2026-03-02 14:00:00.000000', '2026-03-02 14:00:01.000000', 'COMMITTED',   'demo-seed-004'),
    (@member_id, 6, 'UNSUBSCRIBE', 'BASIC',   'NONE',
     '2026-04-01 10:00:00.000000', '2026-04-01 10:00:01.000000', 'COMMITTED',   'demo-seed-005');

-- 도메인 enum 은 외부 implementation 을 모르도록 generic 명명: EXTERNAL_REJECTED.
UPDATE subscription_attempt
SET failure_reason = 'EXTERNAL_REJECTED'
WHERE idempotency_key = 'demo-seed-003';
