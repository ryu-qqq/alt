-- 회원별 LLM 이력 요약 영속 테이블
-- - member_id PK (1:1) + FK 적용
-- - fingerprint: 요약 생성 당시 이력의 식별자 (현재 정책: 최신 COMMITTED attemptId)
-- - summary 변경 시 fingerprint 도 같이 갱신 → 다음 조회의 캐시 hit/miss 판단

CREATE TABLE history_summary (
    member_id   BIGINT       NOT NULL,
    fingerprint BIGINT       NOT NULL,
    summary     TEXT         NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (member_id),
    CONSTRAINT fk_history_summary_member FOREIGN KEY (member_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원별 LLM 이력 요약 (DB 단일 source-of-truth)';
