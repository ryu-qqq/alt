-- alt 구독 서비스 초기 스키마
-- - utf8mb4 / InnoDB / TIMESTAMP(6) (마이크로초)
-- - 회원: 휴대폰번호 UNIQUE
-- - 시도: (member_id, requested_at DESC) 이력 조회용 + idempotency_key UNIQUE
-- - FK 적용 (참조 무결성)
-- - 채널은 명세 예시 6건 시드

CREATE TABLE member (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    phone_number    VARCHAR(11)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원';

CREATE TABLE channel (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='구독/해지 채널';

CREATE TABLE subscription_attempt (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id       BIGINT       NOT NULL,
    channel_id      BIGINT       NOT NULL,
    kind            VARCHAR(20)  NOT NULL,
    from_status     VARCHAR(20)  NOT NULL,
    to_status       VARCHAR(20)  NOT NULL,
    requested_at    TIMESTAMP(6) NOT NULL,
    completed_at    TIMESTAMP(6) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    failure_reason  VARCHAR(40)  NULL,
    failure_detail  TEXT         NULL,
    idempotency_key VARCHAR(64)  NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_idempotency (idempotency_key),
    KEY idx_attempt_member_requested (member_id, requested_at DESC),
    CONSTRAINT fk_attempt_member  FOREIGN KEY (member_id)  REFERENCES member(id),
    CONSTRAINT fk_attempt_channel FOREIGN KEY (channel_id) REFERENCES channel(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='구독/해지 시도 이력';

-- 채널 시드 (명세 예시)
INSERT INTO channel (name, type) VALUES
    ('홈페이지',  'BOTH'),
    ('모바일앱',  'BOTH'),
    ('네이버',    'SUBSCRIBE_ONLY'),
    ('SKT',       'SUBSCRIBE_ONLY'),
    ('콜센터',    'UNSUBSCRIBE_ONLY'),
    ('이메일',    'UNSUBSCRIBE_ONLY');
