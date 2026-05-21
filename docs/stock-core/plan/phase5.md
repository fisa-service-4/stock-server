PHASE 5 — 예수금 + Kafka 연동

목표:
- 은행 ↔ 증권 Saga 연동 지원
- Kafka 이벤트 발행

주의:
Saga orchestration은 transaction-server 책임

────────────────────────────────────

5-1. 내부 API 구축

transaction-server 전용

POST /internal/cash/deposit
POST /internal/cash/withdraw

────────────────────────────────────

5-2. deposit 구현

처리:
- 증권 예수금 증가
- 거래 원장 저장

────────────────────────────────────

5-3. withdraw 구현

처리:
- 증권 예수금 감소
- 거래 원장 저장

────────────────────────────────────

5-4. Kafka 이벤트 발행 구현

[ 성공 ]

- stock.cash.deposit.completed
- stock.cash.withdraw.completed

────────────────────────────────────

[ 실패 ]

- stock.cash.deposit.failed
- stock.cash.withdraw.failed

────────────────────────────────────

5-5. transaction-server 연동

흐름:

transaction-server
→ stock-server API 호출
→ stock-server 처리
→ Kafka 이벤트 발행
→ transaction-server consume

────────────────────────────────────

PHASE 5 완료 기준:

✅ 예수금 Saga API 동작
✅ Kafka 이벤트 발행 가능
✅ transaction-server 연동 가능