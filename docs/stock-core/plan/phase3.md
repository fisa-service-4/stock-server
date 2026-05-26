PHASE 3 — 주문/체결 엔진 구축

목표:
- 주문 생성 및 검증
- 즉시 체결 처리
- 보유종목 쓰기 (매수/매도 시 STOCK_HOLDING 갱신)
- 예수금 갱신 (체결 시 cash_balance 반영)
- 체결 내역 조회

핵심:
stock-server의 가장 중요한 단계
모든 체결 관련 쓰기 로직이 이 Phase에 집중됨

────────────────────────────────────

Phase 5 의존성 오해 정리:

Phase 3의 "매수 — 예수금 >= 주문금액" 검증은
SECURITIES_ACCOUNT.cash_balance 컬럼을 직접 읽으면 됨

Phase 5의 POST /internal/cash/deposit API는
transaction-server가 stock-server를 호출하는 인바운드 API이므로
Phase 3 진행에 Phase 5가 필요하지 않음

────────────────────────────────────

3-1. STOCK_ORDER 구축

API:
POST /internal/v1/stock/accounts/{accountId}/orders
GET  /internal/v1/stock/accounts/{accountId}/orders
GET  /internal/v1/stock/orders/{orderId}
POST /internal/v1/stock/orders/{orderId}/cancel

────────────────────────────────────

3-2. Idempotency-Key 처리

목적:
중복 주문 방지

구현:
- STOCK_ORDER 테이블에 idempotency_key VARCHAR2(255) UNIQUE 컬럼 추가
- POST /orders 요청 시 Idempotency-Key 헤더 추출
- 동일 키로 이미 주문 존재 시 기존 주문 반환

────────────────────────────────────

3-3. Pin-Token 처리

Phase 3에서는 헤더 존재 여부만 검증 (non-null, non-empty)
실제 PIN 토큰 검증은 service-backend 연동 시 확장

────────────────────────────────────

3-4. 주문 흐름 구현

1. Pin-Token 헤더 존재 확인
2. Idempotency-Key 중복 확인
3. AccountValidator.validateOwner(userId, accountId) 호출
   → 계좌 존재 여부(ACCOUNT_001) + 소유자 검증(ACCOUNT_002) + 엔티티 반환
   → 이미 구현 완료: domain/account/validator/AccountValidator.java
4. 주문 검증 (반환된 account.getCashBalance() 직접 사용)
5. 주문 생성 (REQUESTED)
6. 체결 엔진 호출 (@Transactional)

────────────────────────────────────

3-5. 주문 검증 구현

[ 매수 ]
cash_balance >= quantity * price

[ 매도 ]
holding_quantity >= quantity  ← STOCK_HOLDING에서 확인

────────────────────────────────────

3-6. 주문 상태 구현

초기 구현:
- REQUESTED  ← 주문 생성
- FILLED     ← 체결 완료
- CANCELLED  ← 취소
- FAILED     ← 실패

이후 확장 (Phase 3 범위 아님):
- PARTIAL_FILLED  ← 주문장 매칭 엔진 필요

────────────────────────────────────

3-7. STOCK_EXECUTION 구축

목표:
주문 체결 저장

구조:
ORDER 1:N EXECUTION

────────────────────────────────────

3-8. 체결 엔진 구현 (@Transactional 단일 처리)

아래를 하나의 트랜잭션으로 처리:

주문 생성 (REQUESTED)
→ 현재가 조회 (STOCK_PRICE_HISTORY)
→ STOCK_EXECUTION 생성
→ STOCK_HOLDING upsert (보유종목 갱신)
→ SECURITIES_ACCOUNT cash_balance 갱신
→ STOCK_ORDER 상태 변경 (FILLED)

────────────────────────────────────

3-9. MARKET 주문 구현

현재가 즉시 체결

────────────────────────────────────

3-10. LIMIT 주문 구현

조건:
- 매수: 현재가 <= 지정가  → 즉시 체결
- 매도: 현재가 >= 지정가  → 즉시 체결
- 조건 미충족: REQUESTED 상태 유지 (매칭 엔진 없음)

────────────────────────────────────

3-11. STOCK_HOLDING 쓰기 (체결 엔진 내 처리)

[ 매수 체결 시 ]

holding 없으면: INSERT
holding 있으면: 평균단가 재계산 후 UPDATE

평균단가 계산:
(기존매입금액 + 신규매입금액) / (기존수량 + 신규수량)

cash_balance 갱신:
cash_balance -= execution_amount

[ 매도 체결 시 ]

holding_quantity -= executed_quantity
수량 0이면 holding 삭제 가능

cash_balance 갱신:
cash_balance += execution_amount

────────────────────────────────────

3-12. ORDER_MODIFICATION_HISTORY 구축

POST /internal/v1/stock/orders/{orderId}/cancel 처리 시:

ORDER_MODIFICATION_HISTORY INSERT:
- modification_type = CANCEL
- before_payload = 취소 전 주문 상태 (JSON)
- after_payload = 취소 후 주문 상태 (JSON)
- modified_by = USER

Oracle에서 JSON → CLOB 타입 사용

────────────────────────────────────

3-13. STOCK_EXECUTION 조회 API 구현

API:
GET /internal/v1/stock/accounts/{accountId}/executions

Query Parameter:
- stockCode (선택)
- fromDate / toDate (선택, YYYY-MM-DD)
- page / size

────────────────────────────────────

────────────────────────────────────

Issue 구성:

Issue #21: 주문 생성/즉시 체결 엔진/취소 (feat/#21-order-command-engine) ← 완료
Issue #22: 주문/체결 조회 (feat/#22-order-and-execution-query) ← 미완료

────────────────────────────────────

PHASE 3 완료 기준:

[x] STOCK_ORDER 생성 동작: POST /internal/v1/stock/accounts/{accountId}/orders
[x] STOCK_ORDER 목록 조회 동작: GET /internal/v1/stock/accounts/{accountId}/orders
[x] STOCK_ORDER 상세 조회 동작: GET /internal/v1/stock/orders/{orderId}
[x] STOCK_ORDER 취소 동작: POST /internal/v1/stock/orders/{orderId}/cancel
[x] Idempotency-Key 중복 주문 방지 동작
[x] Pin-Token 헤더 존재 검증 동작
[x] AccountValidator.validateOwner() 적용
[x] 매수 검증: cash_balance 부족 시 ORDER_001 반환
[x] 매도 검증: holding_quantity 부족 시 ORDER_002 반환
[x] MARKET 주문 즉시 체결 동작
[x] LIMIT 주문 조건 체결 동작
[x] STOCK_EXECUTION 생성 확인
[x] STOCK_HOLDING upsert (매수 평균단가 / 매도 수량 감소) 동작
[x] cash_balance 갱신 확인
[x] ORDER_MODIFICATION_HISTORY 취소 이력 저장 확인
[x] 체결 조회 동작: GET /internal/v1/stock/accounts/{accountId}/executions
