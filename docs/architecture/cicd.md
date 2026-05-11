# CI/CD 흐름

GitHub Actions → ECR → ECS Rolling Update.

## 전체 흐름

```
[Developer] git push origin main
     │
     ▼
┌──────────────────────────────────────────────────────────┐
│ ci.yml  — push(main) / pull_request(main) 트리거         │
│   1. checkout                                            │
│   2. JDK 21 (temurin) + Gradle 캐시                       │
│   3. ./gradlew build  (전체 모듈: 단위/ArchUnit/통합)     │
│   4. 테스트 리포트 아티팩트 업로드                         │
└────────────────────┬─────────────────────────────────────┘
                     │ success on main
                     ▼
┌──────────────────────────────────────────────────────────┐
│ cd.yml  — workflow_run(CI) 성공 + main 브랜치 트리거      │
│   1. AWS OIDC 자격 획득 (Access Key 저장 X)              │
│   2. ECR 로그인 → docker build → push (sha + latest)     │
│   3. aws ecs update-service --force-new-deployment       │
│   4. ECS Rolling Update — ALB Target Group health check  │
│      통과한 신규 task만 트래픽 수신, 구 task drain        │
│   5. aws ecs wait services-stable — 안정화 확인          │
└──────────────────────────────────────────────────────────┘
```

## CI (`ci.yml`)

| 단계 | 동작 |
|---|---|
| checkout | `actions/checkout@v4` |
| JDK | `actions/setup-java@v4` — temurin 21, Gradle 캐시 활성 |
| Gradle | `gradle/actions/setup-gradle@v3` — wrapper 사용, daemon disable |
| 빌드+테스트 | `./gradlew build` — 단위/ArchUnit + Testcontainers 통합 테스트 모두 |
| 리포트 | 실패 시에도 `**/build/reports/tests` 아티팩트 업로드 |

**Testcontainers는 ubuntu-latest 러너에서 동작** (docker daemon 기본 활성).

PR도 동일 워크플로 → main에 머지되기 전 리그레션 차단.

## CD (`cd.yml`)

### 트리거
`workflow_run` — `CI` 워크플로가 main에서 success로 끝났을 때만.

### 인증 — AWS OIDC

`aws-actions/configure-aws-credentials@v4`로 GitHub OIDC → AWS IAM Role assume.

| 옵션 | 채택 | 근거 |
|---|---|---|
| **OIDC** | ✅ | 장기 자격증명 0. 키 회전/유출 위험 없음. 신뢰 정책으로 특정 레포/브랜치만 허용 가능. |
| Long-lived Access Key | ❌ | GitHub Secrets 노출 시 즉시 침해. 키 회전 운영 부담. |

IAM Role의 신뢰 정책 예시:
```json
{
  "Effect": "Allow",
  "Principal": { "Federated": "arn:aws:iam::<acct>:oidc-provider/token.actions.githubusercontent.com" },
  "Action": "sts:AssumeRoleWithWebIdentity",
  "Condition": {
    "StringEquals": {
      "token.actions.githubusercontent.com:sub": "repo:ryu-qqq/alt:ref:refs/heads/main"
    }
  }
}
```

### 단계
1. `aws-actions/amazon-ecr-login@v2` — ECR 로그인.
2. `docker build` → 이미지 태그 2개:
   - `<short-sha>` — 변경 추적/롤백용
   - `latest` — 운영 편의용
3. ECR push.
4. `aws ecs update-service --force-new-deployment` — 새 이미지로 task 재시작.
5. `aws ecs wait services-stable` — Rolling Update 완료 대기 (최대 10분).

### 배포 검증

ECS Rolling Update 정책:
- `minimumHealthyPercent: 100` — 항상 최소 desiredCount만큼 healthy 보장
- `maximumPercent: 200` — 신/구 동시 최대 2배까지 허용
- 신규 task가 ALB Target Group health check (`GET /actuator/health` 200) 통과해야 등록
- 등록 후 구 task connection draining (기본 30s) → 종료

→ **실패한 이미지는 ALB에 등록되지 않아 자동으로 트래픽이 안 감.** 사실상 자동 보호.

### 롤백

| 시나리오 | 대응 |
|---|---|
| 새 task가 health check 통과 못 함 | `services-stable` wait 실패 → CD 워크플로 fail. 구 task 그대로 트래픽 처리 중. |
| 헬스체크는 통과하지만 5xx 급증 | CloudWatch Alarm → 수동 롤백: `aws ecs update-service --task-definition <previous-revision>` |
| 자동 롤백 필요 | CodeDeploy ECS deployment type으로 진화 ([aws-deployment.md](aws-deployment.md) 참고) |

## 진입점별 워크플로 분리

[ADR-0005](../adr/0005-bootstrap-multi-entry.md)에 따라 진입점이 늘면 CD를 진입점별로 분리한다:

```
.github/workflows/
├── ci.yml                # 전체 빌드/테스트 (변경 없음)
├── cd-api.yml            # bootstrap-api 변경 시 → ECR alt-api → ECS alt-api 서비스
├── cd-scheduler.yml      # 향후
└── cd-worker.yml         # 향후
```

`paths` 필터로 진입점/공유 모듈 변경만 트리거:

```yaml
on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]
  push:
    branches: [main]
    paths:
      - 'bootstrap/bootstrap-api/**'
      - 'application/**'
      - 'domain/**'
      - 'adapter-in/**'
      - 'adapter-out/**'
```

## 시크릿/변수 매핑

| 종류 | 키 | 값 |
|---|---|---|
| Secret | `AWS_ROLE_TO_ASSUME` | IAM Role ARN (OIDC trust) |
| Variable | `AWS_REGION` | `ap-northeast-2` |
| Variable | `ECR_REPOSITORY` | `alt-api` |
| Variable | `ECS_CLUSTER` | `alt-prod` |
| Variable | `ECS_SERVICE` | `alt-api` |

> 애플리케이션 시크릿(DB password, OPENAI_API_KEY)은 GitHub Secrets에 두지 않는다 — 런타임에 ECS task definition의 `secrets` 필드가 AWS Secrets Manager에서 직접 주입.

## 알려진 한계

- **Migration 처리**: 현재는 부팅 시 Flyway 자동 실행. 스키마 변경과 코드 배포 동시 시점에 일어남. 운영 단계에서는 별도 ECS RunTask Job으로 마이그레이션 실행 후 Service update 권장.
- **이미지 태그 전략**: 현재 `<short-sha>` + `latest`. 운영 단계에서 `latest`는 위험 — 명시 sha 태그만 운영하고 latest는 dev only로 갈 것.
- **자동 롤백 없음**: CodeDeploy 도입 시점까지는 수동 롤백.
