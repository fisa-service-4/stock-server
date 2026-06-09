# stock-server 단위 테스트 계획

## 원칙

**금전 리스크 순**으로 우선순위 결정.  
버그 발생 시 실제 돈·수량에 직접 영향을 주는 로직을 먼저 방어한다.

Mock 데이터 서버(`MockStockPriceProvider`, `StockPriceScheduler` 등)는 테스트 대상 제외.

---

## 우선순위

| 순위 | 대상 | 이유 |
|---|---|---|
| 1 | `OrderService` | 주문·체결·예수금·보유수량 동시 변경. 버그 시 서비스 신뢰 훼손 |
| 2 | `StockHolding`, `SecuritiesAccount` | 평단가·잔액 계산 오류 → 수익률 전체 오염 |
| 3 | `CashService` | Saga 연동 예수금 입출금. 잔액 경계값 버그 잦음 |
| 4 | `AccountValidator` | 모든 금융 API 진입점. 소유권 검증 누락 시 타인 자산 접근 |
| 5 | `HoldingService` | 수익률 계산 숫자 검증 (여유 있을 때) |

---

## Phase 1 — OrderService (체결 엔진)

**파일**: `OrderServiceTest.java`

### isExecutable (createOrder를 통해 검증)

| 케이스 | 조건 | 기대 결과 |
|---|---|---|
| MARKET BUY | 항상 | 즉시 체결 |
| LIMIT BUY — 조건 충족 | 현재가(63,000) ≤ 지정가(65,000) | 즉시 체결 |
| LIMIT BUY — 조건 미충족 | 현재가(70,000) > 지정가(65,000) | REQUESTED 유지 |
| LIMIT SELL — 조건 충족 | 현재가(72,000) ≥ 지정가(70,000) | 즉시 체결 |
| LIMIT SELL — 조건 미충족 | 현재가(68,000) < 지정가(70,000) | REQUESTED 유지 |

### validateOrderCondition (createOrder를 통해 검증)

| 케이스 | 조건 | 기대 결과 |
|---|---|---|
| BUY — 잔액 충분 | cashBalance(1,000,000) ≥ required(700,000) | 통과 |
| BUY — 잔액 부족 | cashBalance(100,000) < required(700,000) | ORDER_001 |
| BUY — 잔액 == 주문금액 (경계값) | cashBalance == required | 통과 |
| SELL — 보유수량 충분 | holding(20주) ≥ order(10주) | 통과 |
| SELL — 보유수량 부족 | holding(5주) < order(10주) | ORDER_002 |
| SELL — 보유종목 없음 | holding 없음 | ORDER_002 |

### createOrder 체결 후 상태 검증

| 케이스 | 검증 항목 |
|---|---|
| MARKET BUY 즉시 체결 | status=FILLED, filledQty, remainingQty=0, execution 생성, holding 생성, cashBalance 감소 |
| MARKET BUY (기존 보유 있음) | holding.quantity 증가, holding.averagePrice 재계산 |
| MARKET SELL 즉시 체결 | holding.quantity 감소, cashBalance 증가 |
| MARKET SELL 전량 체결 | holding 삭제 (quantity==0) |
| LIMIT BUY 조건 미충족 | status=REQUESTED, execution 미생성, holding/cash 변동 없음 |

### cancelOrder

| 케이스 | 기대 결과 |
|---|---|
| REQUESTED 상태 취소 | status=CANCELLED, remainingQty=0, ModificationHistory 저장 |
| FILLED 상태 취소 시도 | ORDER_004 |
| 존재하지 않는 주문 | ORDER_003 |

---

## Phase 2 — StockHolding, SecuritiesAccount (Entity)

**파일**: `StockHoldingTest.java`, `SecuritiesAccountTest.java`

### StockHolding.buy()

| 케이스 | 검증 항목 |
|---|---|
| 신규 보유 시 첫 매수 (Builder로 생성 후 buy 불호출) | Builder 생성값 그대로 |
| 추가 매수 평단가 재계산 | 10주@10,000 + 20주@20,000 = 30주@16,667 (HALF_UP) |
| totalPurchaseAmount 갱신 | avgPrice × newQuantity |

### StockHolding.sell()

| 케이스 | 검증 항목 |
|---|---|
| 일부 매도 | quantity 감소, totalPurchaseAmount = avgPrice × newQty |
| 전량 매도 | quantity = 0, totalPurchaseAmount = 0 |

### SecuritiesAccount.deposit() / withdraw()

| 케이스 | 검증 항목 |
|---|---|
| 입금 | cashBalance, withdrawableBalance 증가 |
| 출금 | cashBalance, withdrawableBalance 감소 |
| 출금 경계값: balance == amount | 성공, balance = 0 |

---

## Phase 3 — CashService

**파일**: `CashServiceTest.java`

| 케이스 | 기대 결과 |
|---|---|
| deposit — 정상 | cashBalance 증가, save 호출 |
| deposit — 계좌 없음 | ACCOUNT_001 |
| deposit — 타인 계좌 | ACCOUNT_002 |
| withdraw — 정상 | cashBalance 감소 |
| withdraw — 잔액 == 출금액 (경계값) | 성공, balance = 0 |
| withdraw — 잔액 부족 | TRANSFER_002 |

---

## Phase 4 — AccountValidator

**파일**: `AccountValidatorTest.java`

| 케이스 | 기대 결과 |
|---|---|
| 정상 (계좌 존재 + 소유자 일치) | SecuritiesAccount 반환 |
| 계좌 없음 | ACCOUNT_001 |
| 타인 계좌 | ACCOUNT_002 |

---

## Phase 5 (이후) — HoldingService, ReconciliationService 등

수익률 실수 검증 포함:
- 매입 100만 → 평가 120만 → totalReturnRate = 20.0%
- 전일 110만 → 금일 120만 → dailyReturnRate ≈ 9.09%
