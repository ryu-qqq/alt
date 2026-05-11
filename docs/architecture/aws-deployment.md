# AWS 배포 설계

본 시스템을 AWS에 배포/운영한다고 가정한 설계 문서. 핵심 설계 결정은 **bootstrap 다중 진입점([ADR-0005](../adr/0005-bootstrap-multi-entry.md))** 을 진입점별 컨테이너로 운영하는 것.

## 전체 토폴로지

```
                       ┌──────────────────────────┐
                       │   Route 53 / ACM          │
                       └──────────┬───────────────┘
                                  │ HTTPS
                       ┌──────────▼───────────────┐
                       │   Application Load        │
                       │   Balancer (Public)       │
                       │   Health: /actuator/health│
                       └──────────┬───────────────┘
                                  │ :8080
                  ┌───────────────┴────────────────┐
                  │  Private Subnet (Multi-AZ)     │
                  │                                │
                  │  ┌──────────────────────────┐  │
                  │  │ ECS Fargate Service:     │  │
                  │  │ alt-api  (bootstrap-api) │  │
                  │  │  - desired: 2 (min 2)    │  │
                  │  │  - target tracking ASG   │  │
                  │  └──────────────────────────┘  │
                  │  ┌──────────────────────────┐  │
                  │  │ ECS Fargate Service:     │  │
                  │  │ alt-scheduler            │  │  ← 향후 추가
                  │  │ (bootstrap-scheduler)    │  │
                  │  │  - desired: 1 (단일)     │  │
                  │  └──────────────────────────┘  │
                  │  ┌──────────────────────────┐  │
                  │  │ ECS Fargate Service:     │  │
                  │  │ alt-worker               │  │  ← 향후 추가
                  │  │ (bootstrap-worker)       │  │
                  │  │  - SQS 큐 길이 기반 ASG  │  │
                  │  └──────────────────────────┘  │
                  │              │                 │
                  │              ▼                 │
                  │  ┌──────────────────────────┐  │
                  │  │ RDS MySQL 8 (Multi-AZ)   │  │
                  │  │  + 자동 백업 / PITR      │  │
                  │  └──────────────────────────┘  │
                  └────────────────────────────────┘
                                  │
                  ┌───────────────┼─────────────────┐
                  ▼               ▼                 ▼
           Secrets Manager   ECR Registry    CloudWatch Logs
           (DB pw, LLM key)  (이미지 보관)   + Container Insights

                                  │
                                  ▼
                          External APIs
                          - csrng.net
                          - api.openai.com
```

## 컴퓨트 — ECS Fargate (진입점별 Service)

### 왜 Fargate인가

| 옵션 | 채택 여부 | 근거 |
|---|---|---|
| **ECS Fargate** | ✅ | 운영 부담 최소(노드 관리 없음), Auto Scaling 직관적, 진입점별 분리 친화 |
| ECS on EC2 | ❌ | 노드 관리 비용 > 본 과제 규모에서 얻는 이득 |
| EKS | ❌ | 과제 규모 대비 클러스터 운영 비용 과함. 마이크로서비스 다수 시 재검토 |
| Lambda | ❌ | Spring Boot 콜드 스타트 + 트래픽 패턴 부적합 |

### 진입점별 Service 분리 — 핵심 설계

[ADR-0005](../adr/0005-bootstrap-multi-entry.md)의 다중 진입점 패턴이 배포 단계에서 다음 가치를 만든다:

| 진입점 | ECR 이미지 | ECS Service | Auto Scaling | ALB | IAM Role |
|---|---|---|---|---|---|
| **bootstrap-api** | `alt-api:<sha>` | `alt-api` | CPU/Req 기반 | ✅ Target Group | DB 읽기/쓰기, csrng/LLM 호출, Caffeine만 (캐시 외부 X) |
| **bootstrap-scheduler** (예시) | `alt-scheduler:<sha>` | `alt-scheduler` | 단일 인스턴스 | ❌ | DB 읽기/쓰기만, 외부 API 호출 X |
| **bootstrap-worker** (예시) | `alt-worker:<sha>` | `alt-worker` | SQS 큐 길이 기반 | ❌ | DB + LLM API key + SQS Consumer |

→ 진입점별로 **공격 표면 / 시크릿 스코프 / 스케일링 정책 / 리소스 할당** 이 분리된다. API 트래픽 폭증이 Scheduler를 죽이지 않고, Worker의 LLM API key가 API 컨테이너에 노출되지 않는다.

### Task 정의 (예: bootstrap-api)

| 항목 | 값 |
|---|---|
| CPU / Memory | 1 vCPU / 2 GB (시작) |
| 컨테이너 포트 | 8080 |
| 헬스체크 | `GET /actuator/health` (200 OK) — ALB Target Group + ECS 모두 |
| 로그 드라이버 | `awslogs` → CloudWatch Logs |
| 환경변수 | DB_HOST, DB_PORT, DB_NAME (비민감) |
| 시크릿 | DB_PASSWORD, OPENAI_API_KEY ← Secrets Manager에서 ARN 주입 |

## 데이터 — RDS MySQL 8 Multi-AZ

| 항목 | 값 | 근거 |
|---|---|---|
| 엔진 | MySQL 8.0 | 도메인 + Flyway 친화 |
| 인스턴스 | `db.r6g.large` (시작) | 메모리 우선 워크로드 (이력 조회) |
| Multi-AZ | ✅ | 스탠바이 AZ 자동 페일오버 — RPO ≈ 0, RTO < 60s |
| 백업 | 자동 백업 7일 + PITR (Point-in-Time Recovery) | 데이터 손실 위험 완화 |
| 파라미터 그룹 | `time_zone=Asia/Seoul`, `character_set_server=utf8mb4` | 한글/타임존 |
| 접근 제어 | Private Subnet, Security Group으로 ECS Task만 허용 | 외부 노출 0 |

### Flyway 마이그레이션
- 옵션 1 (현재): 애플리케이션 부팅 시 자동 실행 (`spring.flyway.enabled=true`).
- 옵션 2 (운영 단계): 별도 ECS RunTask로 마이그레이션 전용 Job 실행 → 부팅 시 Flyway 비활성화. Blue/Green 배포 시 스키마 변경과 코드 배포 분리에 유리.

## 캐시 — Caffeine (현재) → ElastiCache Redis (분산 시)

[ADR-0004](../adr/0004-idempotency-strategy.md)에 따라 멱등성 게이트는 Caffeine in-process. 단일 인스턴스 가정.

**다중 인스턴스로 진화 시**:
- `adapter-out/cache-redis` 모듈 신설 → ElastiCache Redis(Cluster Mode) 어댑터 구현.
- bootstrap-api의 implementation을 `cache-caffeine` → `cache-redis`로 교체.
- Application 코드 변경 0.

## 시크릿 — AWS Secrets Manager

| 시크릿 | 사용 진입점 |
|---|---|
| `alt/prod/db` (username, password) | api, scheduler, worker |
| `alt/prod/openai-api-key` | api, worker |
| `alt/prod/csrng-api-key` (현재 불필요, 미래 대비) | api, worker |

ECS Task Definition의 `secrets` 필드로 ARN 주입 → 환경변수로 자동 로딩. 코드/이미지에 자격증명 없음.

**KMS**: 시크릿/RDS storage/EBS 모두 고객 관리 KMS 키(CMK)로 암호화.

## 네트워크

| 컴포넌트 | Subnet | Security Group |
|---|---|---|
| ALB | Public (Multi-AZ) | Inbound 443 from 0.0.0.0/0 |
| ECS Task (api) | Private (Multi-AZ) | Inbound 8080 from ALB SG |
| ECS Task (scheduler/worker) | Private | Inbound 없음 (egress only) |
| RDS | Private (DB Subnet Group) | Inbound 3306 from ECS Task SG |
| NAT Gateway | Public (AZ별) | ECS Task egress (csrng/LLM 호출) |

**VPC Endpoint** 추가 (NAT 비용/지연 감소):
- Secrets Manager (Interface)
- ECR (Interface) + ECR API
- CloudWatch Logs (Interface)
- S3 (Gateway, ECR 레이어용)

## 배포 — ECR + ECS Rolling Update

### 흐름

```
GitHub Actions push to main
   │
   ├─ ./gradlew build (전체 모듈 테스트 포함)
   │
   ├─ docker build → ECR push (진입점별 이미지)
   │   - alt-api:<git-sha>
   │   - alt-api:latest (alias)
   │
   └─ aws ecs update-service --force-new-deployment
       │
       └─ ECS Rolling Update
           - minimumHealthyPercent: 100
           - maximumPercent: 200
           - 신규 task 등록 → ALB Target Group health check pass → 구 task drain
           → 무중단 배포 효과
```

### Blue/Green이 필요해지는 순간

ECS Rolling Update는 신/구 task가 **잠시 공존**한다. 다음이 필요해지면 **CodeDeploy ECS deployment type**으로 전환:
- 트래픽 가중치 단계적 전환 (예: 10% → 50% → 100%)
- 자동 롤백 (CloudWatch Alarm 기반)
- Listener 수준의 traffic shift

본 과제 규모에서는 Rolling Update로 충분. 트래픽이 큰 운영 단계에서 재검토.

CI/CD 상세는 [cicd.md](cicd.md) 참고.

## 관측성

| 영역 | 도구 | 메트릭 |
|---|---|---|
| 로그 | CloudWatch Logs (Log Group: `/ecs/alt-api`) | 애플리케이션/액세스 로그. Logback JSON 인코더로 구조화. |
| 메트릭 | CloudWatch Container Insights + Micrometer | CPU/Mem/Network, JVM heap, GC, request/error rate |
| 외부 API | Resilience4j 메트릭 → CloudWatch Metrics | csrng/LLM 실패율, CB 상태(OPEN/HALF_OPEN/CLOSED), Retry 횟수 |
| 분산 추적 | (옵션) AWS X-Ray | API → DB / 외부 API 호출 latency 분석 |
| 알람 | CloudWatch Alarms | 5xx 비율 / CB OPEN / Attempt FAILED 급증 / DB 커넥션 풀 고갈 |

### 핵심 알람 예시
- `CSRNG CB OPEN`: csrng CB가 OPEN 상태 5분 지속 → SNS → Slack
- `Attempt FAILED 급증`: 1분간 FAILED 비율 > 20% → 알람
- `RDS Connection Saturation`: 연결 사용률 > 80% → 알람

## 보안

| 영역 | 정책 |
|---|---|
| IAM | 진입점별 Task Role 분리. `alt-worker`는 SQS Consumer 권한, `alt-api`는 SQS 권한 없음. 최소 권한 원칙. |
| 시크릿 | 코드/이미지/환경변수 평문 0. Secrets Manager → ECS secrets로 주입. |
| 네트워크 | RDS/ECS Task는 Private Subnet. ALB만 Public. csrng/LLM 호출은 NAT Gateway egress (Egress 화이트리스트 가능). |
| 전송 암호화 | HTTPS (ACM 인증서) at ALB. 내부 통신은 VPC 내부. |
| 저장 암호화 | RDS storage / EBS / Secrets Manager 모두 KMS CMK. |
| 컨테이너 이미지 | ECR Image Scanning (Enhanced) 활성화. 취약점 발견 시 알람. |
| 감사 | CloudTrail 전체 활성화 + 90일 보관. |

## 확장성

| 단계 | 변경 |
|---|---|
| **현재 (단일 인스턴스 가정)** | bootstrap-api 1개 컨테이너, Caffeine in-process, RDS Single-AZ로도 가능 |
| **소규모 운영 (~100 RPS)** | bootstrap-api 2~4개, RDS Multi-AZ, ALB |
| **중규모 (~1000 RPS)** | + ElastiCache Redis (멱등성 분산), RDS Read Replica (history 조회 분산) |
| **대규모** | + Worker 분리 (LLM 호출 비동기화 via SQS), API 가벼움 유지, RDS 샤딩 검토 |

Auto Scaling:
- **bootstrap-api**: Target Tracking — CPU 60% / ALB Request Count per Target 기반.
- **bootstrap-worker** (향후): SQS `ApproximateNumberOfMessages` 기반.
- **bootstrap-scheduler** (향후): 단일 인스턴스 유지 (ECS Service desired=1, scheduler 중복 실행 방지).

## 비용 추산 (월, ap-northeast-2 기준 대략치)

| 항목 | 사양 | 월 비용 (USD) |
|---|---|---|
| ECS Fargate (api) | 1 vCPU / 2GB × 2 task × 730h | ~$60 |
| RDS MySQL Multi-AZ | db.r6g.large (Multi-AZ) | ~$280 |
| ALB | + 데이터 처리 | ~$25 |
| NAT Gateway | + 데이터 전송 | ~$45 |
| Secrets Manager | 3 시크릿 + API 호출 | ~$2 |
| CloudWatch Logs | 10GB 수집 + 30일 보관 | ~$15 |
| ECR | 10GB 저장 | ~$1 |
| **합계 (대략)** | | **~$430** |

> Worker/Scheduler 추가 시 진입점당 ECS Fargate 비용 증가. 필요 시 1 vCPU / 0.5~1GB로 작게 시작.

## 알려진 운영 이슈와 대응

| 이슈 | 대응 |
|---|---|
| PENDING 좀비 attempt (앱이 csrng 응답 받기 전 다운) | bootstrap-scheduler에 정리 잡 — 5분 이상 PENDING → FAILED 전환 |
| csrng 영구 장애 | CB OPEN 알람 + 사용자 안내 페이지 |
| LLM API 비용 폭증 | History summary 캐시 hit rate 모니터링, daily 호출 quota 알람 |
| RDS 페일오버 시 단기 connection 끊김 | HikariCP `connection-test-query`, retry-on-failure 설정 |
| 로그 PII 노출 (휴대폰번호) | Logback 마스킹 필터 (예: `010-****-1234`) — 향후 작업 |

## 진화 로드맵

1. **현재**: bootstrap-api 단일 진입점, Caffeine, Single-AZ로도 동작.
2. **+ Multi-AZ**: RDS Multi-AZ + ECS 다중 task. 가용성 확보.
3. **+ ElastiCache Redis**: 멱등성 분산. cache-redis 어댑터 모듈 신설.
4. **+ bootstrap-scheduler**: PENDING 좀비 정리, History summary 캐시 무효화.
5. **+ bootstrap-worker + SQS**: LLM 호출 비동기화. API 응답 latency 향상.
6. **+ CodeDeploy Blue/Green**: 트래픽 가중치 단계 전환, 자동 롤백.
7. **+ X-Ray 분산 추적**: 외부 API 응답 시간 분석.
