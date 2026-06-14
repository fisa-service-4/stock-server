# 주문·체결 엔진

> `OrderService.createOrder()` 를 중심으로 한 주문 생성부터 체결까지의 전체 흐름.

---

## 1. 전체 흐름

```
POST /internal/v1/stock/accounts/{accountId}/orders
  Headers: X-User-Id, Pin-Token, Idempotency-Key

1. Pin-Token 헤더 존재 검증
2. Idempotency-Key 중복 확인
3. AccountValidator.validateOwner(userId, accountId)
4. 종목 존재 확인 (STOCK_001)
5. 현재가 조회 (StockPriceHistoryService)
6. 매수/매도 검증
7. StockOrder 생성 (status=REQUESTED)
8. tryExecute() — 체결 엔진
9. 201 Created 반환
```

---

## 2. 사전 검증 (체결 전)

### Pin-Token 검증

```java
String pinToken = request.getHeader("Pin-Token");
if (pinToken == null || pinToken.isBlank()) {
    throw new GlobalException(ErrorCode.VALID_002);
}
```

금융 거래 API는 PIN 토큰 헤더가 반드시 존재해야 한다.
현재 구현은 **존재 여부만 검증**한다 (내용 검증은 service-backend PIN 인증 후 헤더 전달 방식으로 설계).

### Idempotency-Key 처리

```java
String idempotencyKey = request.getHeader("Idempotency-Key");
stockOrderRepository.findByIdempotencyKey(idempotencyKey)
    .ifPresent(existing -> { return existing; });  // 기존 주문 반환
```

`STOCK_ORDER.idempotency_key` 컬럼에 UNIQUE 제약이 걸려 있다.
네트워크 재시도나 중복 클릭으로 인한 이중 주문을 DB 레벨에서도 방지한다.

### AccountValidator

```java
// domain/account/validator/AccountValidator.java
SecuritiesAccount validateOwner(Long userId, Long accountId) {
    SecuritiesAccount account = repository.findById(accountId)
        .orElseThrow(() -> new GlobalException(ACCOUNT_001));  // 계좌 없음

    if (!account.getUserId().equals(userId)) {
        throw new GlobalException(ACCOUNT_002);  // 타인 계좌 접근
    }
    return account;   // 이후 서비스에서 엔티티 재사용
}
```

반환된 `account` 엔티티를 체결 엔진까지 그대로 전달해 DB 재조회를 피한다.

### 매수/매도 검증

**매수 (BUY)**
```java
BigDecimal requiredCash = (orderMethod == MARKET)
    ? currentPrice.multiply(qty)
    : price.multiply(qty);         // LIMIT은 지정가 기준으로 검증

if (account.getCashBalance().compareTo(requiredCash) < 0) {
    throw new GlobalException(ORDER_001);
}
```

**매도 (SELL)**
```java
StockHolding holding = holdingRepository
    .findBySecuritiesAccountIdAndStockCode(accountId, stockCode)
    .orElseThrow(() -> new GlobalException(ORDER_002));

if (holding.getHoldingQuantity() < quantity) {
    throw new GlobalException(ORDER_002);
}
```

---

## 3. 체결 엔진 — tryExecute()

**파일:** `domain/order/service/OrderService.java`

체결 가능 여부 판정 → 체결 불가 시 조기 리턴 (주문은 REQUESTED 상태 유지).

### 체결 조건 — isExecutable()

| 주문 방식 | 조건 | 설명 |
|---|---|---|
| `MARKET` | 항상 체결 | 현재가 즉시 체결 |
| `LIMIT BUY` | `currentPrice <= orderPrice` | 지정가 이하일 때만 체결 |
| `LIMIT SELL` | `currentPrice >= orderPrice` | 지정가 이상일 때만 체결 |

LIMIT 조건 미충족 시 REQUESTED 상태로 유지된다.
`PendingOrderScheduler`가 5초 주기로 REQUESTED 주문을 재시도하므로 이후에도 체결 기회가 있다.
자세한 내용은 아래 [6. 미체결 주문 재처리](#6-미체결-주문-재처리--pendingorderscheduler) 참고.

### 체결 처리 — @Transactional 단일 처리

```
tryExecute(order, account, currentPrice)
  │
  ├── 1. StockExecution 생성·저장
  │       executedPrice   = currentPrice
  │       executedQuantity = order.getOrderQuantity()
  │       executionAmount  = currentPrice × quantity
  │
  ├── 2. applyHolding(order, currentPrice, executionAmount, account)
  │       (아래 4번 상세)
  │
  ├── 3. 예수금 갱신
  │       BUY:  account.withdraw(executionAmount)   // cashBalance 차감
  │       SELL: account.deposit(executionAmount)    // cashBalance 증가
  │       securitiesAccountRepository.save(account)
  │
  └── 4. 주문 상태 전환
          order.fill(quantity, currentPrice)
            → filledQuantity += quantity
            → remainingQuantity -= quantity
            → status = FILLED
```

**5개 엔티티 변경이 하나의 `@Transactional` 안에서 처리된다.**
`StockExecution`, `StockHolding`, `SecuritiesAccount`, `StockOrder` 중 하나라도 실패하면 전체 롤백.

---

## 4. 보유 종목 처리 — applyHolding()

### 매수 (BUY)

```
기존 보유 없음 → INSERT
  holdingQuantity     = quantity
  averagePurchasePrice = currentPrice (소수점 반올림)
  totalPurchaseAmount  = price × quantity

기존 보유 있음 → 평균단가 재계산 후 UPDATE
  newTotalAmount  = (기존 totalPurchaseAmount) + (currentPrice × quantity)
  newQuantity     = (기존 holdingQuantity) + quantity
  newAvgPrice     = newTotalAmount / newQuantity
```

평균단가는 누적 매입금액 기준으로 계산한다.
`holding.buy(quantity, executionPrice)` 엔티티 메서드 내에서 처리.

### 매도 (SELL)

```
holding.sell(quantity)
  → holdingQuantity -= quantity

holdingQuantity == 0 → DELETE (보유 종목에서 제거)
holdingQuantity  > 0 → UPDATE (잔여 수량 저장)
```

---

## 5. 주문 취소 — cancelOrder()

```
POST /internal/v1/stock/orders/{orderId}/cancel

1. 주문 조회 (ORDER_003)
2. 소유자 확인 (AccountValidator)
3. 상태 확인: REQUESTED 상태만 취소 가능 (ORDER_004)
4. order.cancel() → status = CANCELLED
5. OrderModificationHistory INSERT
     modification_type = CANCEL
     before_payload    = 취소 전 주문 상태 (JSON → CLOB)
     modified_by       = USER
```

이미 `FILLED` 또는 `CANCELLED` 상태인 주문은 취소 불가 (`ORDER_004`).

---

## 6. 미체결 주문 재처리 — PendingOrderScheduler

**파일:** `domain/order/scheduler/PendingOrderScheduler.java`

주문 생성 시 LIMIT 조건이 맞지 않아 REQUESTED로 남은 주문을 주기적으로 재시도한다.

```
@Scheduled(fixedDelayString = "${stock.order.match-interval:5000}")
matchPendingOrders()
  │
  ├── REQUESTED 상태 주문 최대 1000건 조회 (PageRequest)
  ├── stockCode 기준으로 그룹핑
  │     → 종목당 현재가 조회 1회 (N+1 방지)
  │
  └── 각 주문별 orderService.executePendingOrder(orderId, currentPrice)
        ├── 주문 재조회 → REQUESTED 상태 재확인 (취소된 경우 skip)
        ├── 계좌 조회
        └── tryExecute() — 기존 체결 엔진과 동일 로직
```

**설계 포인트**

| 포인트 | 설명 |
|---|---|
| 종목별 그룹핑 | 같은 종목 주문이 여러 개여도 가격 조회는 1번만 |
| 주문 재조회 | 스케줄러 실행 중 취소된 주문을 체결하지 않도록 상태 재확인 |
| 조건 없음 | `@ConditionalOnProperty` 없이 항상 활성화 (Mock/KIS 모드 무관) |
| 독립 try-catch | 한 주문 실패가 다른 주문 처리를 막지 않음 |

설정:
```yaml
stock:
  order:
    match-interval: 5000   # ms, 기본값 5초
```

## 7. 주요 에러 코드

| 코드 | HTTP | 발생 시점 |
|---|---|---|
| `VALID_002` | 400 | Pin-Token 헤더 없음 |
| `ACCOUNT_001` | 404 | 계좌 없음 |
| `ACCOUNT_002` | 403 | 타인 계좌 접근 |
| `STOCK_001` | 404 | 종목 없음 |
| `STOCK_002` | 500 | 현재가 조회 실패 |
| `ORDER_001` | 400 | 매수 가능 금액 부족 |
| `ORDER_002` | 400 | 매도 가능 수량 부족 |
| `ORDER_003` | 404 | 주문 없음 |
| `ORDER_004` | 400 | 취소 불가 상태 |
| `ORDER_005` | 500 | 체결 엔진 내부 오류 |

---

## 8. 기술적 포인트 정리

**Idempotency 이중 방어**
- 애플리케이션 레벨: `idempotencyKey` 헤더 확인 후 기존 주문 반환
- DB 레벨: `UNIQUE` 제약으로 중복 INSERT 자체를 차단

**예수금 검증 기준**
- LIMIT 매수는 `지정가 × 수량`으로 검증 (현재가보다 높은 가격에 주문 가능성 고려)
- MARKET 매수는 `현재가 × 수량`으로 검증 (즉시 체결 기준)

**LIMIT 미체결 처리**
- 조건 불충족 시 주문은 `REQUESTED` 상태로 저장되고 체결 없이 종료
- `PendingOrderScheduler`가 5초 주기로 재시도 → 이후 가격이 조건을 충족하면 자동 체결

**체결 원자성**
- 단일 `@Transactional` 안에서 Execution 저장 → Holding upsert → 예수금 갱신 → Order 상태 변경이 순서대로 처리
- 중간 실패 시 전체 롤백 → 체결 상태와 예수금이 불일치하는 상황 방지
