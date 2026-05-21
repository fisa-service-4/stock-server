PHASE 5 — 예수금 + Kafka 연동

목표:
- transaction-server Saga 연동용 내부 API 구축
- Kafka 이벤트 발행

주의:
Saga orchestration은 transaction-server 책임
stock-server는 이벤트 발행만 담당 (Consumer 불필요)

────────────────────────────────────

5-0. 사전 설정 (Phase 5 시작 전)

build.gradle에 Kafka 의존성 추가:
implementation 'org.springframework.kafka:spring-kafka'

application.yaml에 Kafka 설정 추가:
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

────────────────────────────────────

5-1. 내부 API 구축

transaction-server 전용 (인바운드 API)

POST /internal/v1/cash/deposit
POST /internal/v1/cash/withdraw

────────────────────────────────────

5-2. deposit 구현

처리:
- 예수금 검증 (필요 시)
- SECURITIES_ACCOUNT.cash_balance 증가
- Kafka 이벤트 발행

────────────────────────────────────

5-3. withdraw 구현

처리:
- 예수금 잔액 검증 (cash_balance >= 출금액)
- SECURITIES_ACCOUNT.cash_balance 감소
- Kafka 이벤트 발행

────────────────────────────────────

5-4. Kafka 이벤트 발행 구현

[ 현금 이벤트 ]

성공:
- stock.cash.deposit.completed
- stock.cash.withdraw.completed

실패:
- stock.cash.deposit.failed
- stock.cash.withdraw.failed

[ 주문 이벤트 ] ← stock-core-index.md 명시 항목

성공:
- stock.order.completed

실패:
- stock.order.failed

────────────────────────────────────

5-5. 원자성 한계 (알려진 문제)

현재 구현의 한계:
@Transactional 내에서 예수금 변경 + Kafka publish 처리 시
DB 커밋 후 Kafka publish 실패 → 이벤트 유실 가능

Phase 5는 "동작하지만 내구성 미보장" 상태로 완료
→ Phase 6의 Outbox 패턴으로 해결

────────────────────────────────────

5-6. transaction-server 연동

흐름:

transaction-server
→ POST /internal/v1/cash/deposit (or withdraw) 호출
→ stock-server 처리
→ Kafka 이벤트 발행
→ transaction-server consume

────────────────────────────────────

PHASE 5 완료 기준:

✅ spring-kafka 의존성 및 Kafka 연결 설정 완료
✅ POST /internal/v1/cash/deposit 동작
✅ POST /internal/v1/cash/withdraw 동작 (잔액 부족 시 에러)
✅ cash 이벤트 4종 Kafka 발행 확인 (kafka-ui로 확인)
✅ stock.order.completed / stock.order.failed 이벤트 발행 확인
✅ 원자성 한계 문서화 (Phase 6 필요성 명시)
