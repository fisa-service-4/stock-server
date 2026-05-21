PHASE 6 — Outbox/Event 안정화

목표:
- Kafka 장애 상황에서도 이벤트 유실 방지
- 원장 정합성 유지

배경:
Phase 5에서 "예수금 변경 + Kafka publish"를 @Transactional로 처리하더라도
Kafka는 트랜잭션 대상이 아니므로 이벤트 유실 가능
→ Outbox 패턴으로 해결

────────────────────────────────────

6-1. OUTBOX_EVENT 구축

컬럼:
- outbox_id      ← PK
- event_type     ← VARCHAR2(100)
- payload        ← CLOB (JSON)
- status         ← VARCHAR2(20): PENDING / PUBLISHED / FAILED
- retry_count    ← NUMBER (기본 0)
- created_at     ← TIMESTAMP
- published_at   ← TIMESTAMP (nullable)

────────────────────────────────────

6-2. 동일 Transaction 처리 구현

핵심:
예수금 변경 + OUTBOX_EVENT INSERT를 동일 @Transactional로 처리

흐름:
1. cash_balance 변경 (SECURITIES_ACCOUNT UPDATE)
2. OUTBOX_EVENT INSERT (status = PENDING)
→ 두 작업이 하나의 DB 트랜잭션으로 커밋됨
→ Kafka publish는 Relay Worker가 별도로 처리

적용 범위:
우선 현금 이벤트 (deposit / withdraw)에만 적용
→ 시간 여유 있을 때 order 이벤트로 확장

────────────────────────────────────

6-3. Relay Worker 구축

구현:
@Scheduled (fixedDelay = 5000, 5초 주기)

역할:
OUTBOX_EVENT WHERE status = PENDING 조회
→ Kafka publish
→ 성공: status = PUBLISHED, published_at = now()
→ 실패: status = FAILED, retry_count++

별도 프로세스 불필요:
@Scheduled 메서드로 동일 애플리케이션 내에서 처리

────────────────────────────────────

6-4. 이벤트 상태 관리

상태:
- PENDING   ← DB 저장 직후
- PUBLISHED ← Kafka 발행 성공
- FAILED    ← 발행 실패

────────────────────────────────────

6-5. 재시도 로직 구현

FAILED 이벤트 재시도:
retry_count < 3 인 FAILED 이벤트 → 재발행 시도
retry_count >= 3 → 더 이상 재시도 안 함 (수동 확인 필요)

────────────────────────────────────

6-6. Kafka 장애 대응

Kafka 장애 시:
- 원장 데이터(예수금)는 DB에 안전하게 저장됨
- OUTBOX_EVENT가 PENDING 상태로 유지
- Kafka 복구 후 Relay Worker가 자동 재발행

────────────────────────────────────

시간이 부족한 경우:

Phase 5에서 멈추고 아래를 문서화:
- "현재 구현은 Kafka publish 원자성 미보장"
- "DB 커밋 후 Kafka 장애 시 이벤트 유실 가능"
- "Phase 6 Outbox 패턴으로 해결 예정"

Phase 6은 기능 추가가 아닌 내구성 확보임

────────────────────────────────────

PHASE 6 완료 기준:

✅ OUTBOX_EVENT 엔티티 및 저장 동작
✅ 예수금 변경 + OUTBOX INSERT 동일 트랜잭션 확인
✅ Relay Worker 5초 주기 PENDING → PUBLISHED 처리 확인
✅ Kafka 발행 실패 시 FAILED 상태 전환 확인
✅ retry_count < 3 재시도 동작 확인
✅ Kafka 장애 후 복구 시 이벤트 재발행 동작 확인
