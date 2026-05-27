# stock-server API 컴팩트 레퍼런스

> stock-server 개발 전용 요약 문서.
> 원본: `docs/api/api-stock-server.md`, `docs/api/error-code.md`, `docs/api/api-convention.md`
> 다른 서버 API 문서는 이 파일로 대체 — 매번 전체 읽기 불필요.

---

## 서버 기본 정보

| 항목 | 값 |
|---|---|
| Port | `8082` |
| Base URL | `/internal/v1/stock` |
| 인증 | JWT 없음. 내부 통신 전용 |
| DB | Oracle XE 21c |

---

## 공통 헤더 (요청)

| 헤더 | 필수 | 설명 |
|---|---|---|
| `X-User-Id` | O | 사용자 식별 ID |
| `X-Trace-Id` | O | 요청 추적 ID (없으면 UUID 생성) |

---

## 공통 응답 포맷

```json
// 성공
{ "success": true, "data": {}, "meta": { "traceId": "uuid" } }

// 실패
{ "success": false, "error": { "code": "ORDER_001", "message": "..." }, "meta": { "traceId": "uuid" } }

// 페이지네이션 (목록)
{ "success": true, "data": { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }, "meta": { "traceId": "uuid" } }
```

---

## 에러 코드 (stock-server 관련)

| 코드 | HTTP | 설명 |
|---|---|---|
| `STOCK_001` | 404 | 종목 없음 |
| `STOCK_002` | 500 | 현재가 조회 실패 |
| `STOCK_003` | 500 | 차트 데이터 조회 실패 |
| `ORDER_001` | 400 | 주문 가능 금액 부족 |
| `ORDER_002` | 400 | 보유 수량 부족 |
| `ORDER_003` | 404 | 주문 정보 없음 |
| `ORDER_004` | 400 | 주문 상태 오류 |
| `ORDER_005` | 500 | 주문 실행 실패 |
| `EXECUTION_001` | 404 | 체결 내역 없음 |
| `HOLDING_001` | 404 | 보유 종목 없음 |
| `ACCOUNT_001` | 404 | 계좌 없음 |
| `ACCOUNT_002` | 403 | 본인 계좌 아님 |
| `VALID_001` | 400 | 입력값 오류 |
| `VALID_002` | 400 | 필수값 누락 |

---

## API 목록

### 헬스 체크
**GET** `/internal/v1/stock/health`
```json
{ "success": true, "data": { "database": "UP", "server": "UP" }, "meta": { "traceId": "uuid" } }
```

---

### STOCK-SEARCH-001. 종목 검색
**GET** `/internal/v1/stock/search?keyword={keyword}`

```json
{
  "success": true,
  "data": {
    "content": [
      { "stockCode": "005930", "stockName": "삼성전자", "market": "KOSPI", "currentPrice": 82000, "changeRate": -1.2 }
    ]
  },
  "meta": { "traceId": "uuid" }
}
```
> 결과 없으면 `STOCK_001`

---

### STOCK-PRICE-001. 현재가 조회
**GET** `/internal/v1/stock/{stockCode}/price`

```json
{
  "success": true,
  "data": { "stockCode": "005930", "stockName": "삼성전자", "currentPrice": 82000, "changeRate": -1.2, "updatedAt": "2026-05-17T12:00:00" },
  "meta": { "traceId": "uuid" }
}
```
> `STOCK_001` (종목 없음) / `STOCK_002` (현재가 없음)

---

### STOCK-CHART-001. 차트 조회
**GET** `/internal/v1/stock/{stockCode}/charts`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `interval` | O | `DAILY` / `WEEKLY` / `MONTHLY` |
| `fromDate` | X | `YYYY-MM-DD` |
| `toDate` | X | `YYYY-MM-DD` |

```json
{
  "success": true,
  "data": {
    "content": [
      { "date": "2026-05-17", "open": 81000, "high": 82500, "low": 80500, "close": 82000, "volume": 12345678 }
    ]
  },
  "meta": { "traceId": "uuid" }
}
```
> `STOCK_001` / `STOCK_003` (데이터 없음)

---

### STOCK-ACCOUNT-001. 주문 가능 계좌 조회
**GET** `/internal/v1/stock/accounts`

```json
{
  "success": true,
  "data": {
    "content": [
      { "accountId": 2001, "accountNumber": "300-123-456789", "accountName": "내 주식 계좌", "bankCode": "039" }
    ]
  },
  "meta": { "traceId": "uuid" }
}
```

---

### STOCK-ACCOUNT-002. 예수금 조회
**GET** `/internal/v1/stock/accounts/{accountId}/cash-balance`

```json
{
  "success": true,
  "data": { "accountId": 2001, "cashBalance": 3000000, "availableBalance": 2800000 },
  "meta": { "traceId": "uuid" }
}
```

---

### STOCK-ORDER-001. 주문 생성
**POST** `/internal/v1/stock/accounts/{accountId}/orders`
> Write API

**Request Body**
```json
{ "stockCode": "005930", "orderType": "BUY", "orderMethod": "LIMIT", "quantity": 10, "price": 82000 }
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `stockCode` | O | 종목 코드 |
| `orderType` | O | `BUY` / `SELL` |
| `orderMethod` | O | `MARKET` / `LIMIT` |
| `quantity` | O | 주문 수량 |
| `price` | LIMIT만 | 주문 가격 |

**Response `201`**
```json
{
  "success": true,
  "data": {
    "orderId": 1001, "stockCode": "005930", "orderType": "BUY", "orderMethod": "LIMIT",
    "quantity": 10, "price": 82000, "filledQuantity": 0, "remainingQuantity": 10,
    "status": "REQUESTED", "orderedAt": "2026-05-17T12:00:00"
  },
  "meta": { "traceId": "uuid" }
}
```
> `ORDER_001` (금액 부족) / `ORDER_002` (보유 수량 부족)

---

### STOCK-ORDER-002. 주문 조회 (목록)
**GET** `/internal/v1/stock/accounts/{accountId}/orders`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `status` | X | `REQUESTED` / `PARTIAL_FILLED` / `FILLED` / `CANCELLED` / `FAILED` / `REJECTED` / `EXPIRED` |
| `orderType` | X | `BUY` / `SELL` |
| `page` | X | 기본값 0 |
| `size` | X | 기본값 20 |

```json
{
  "success": true,
  "data": {
    "content": [
      { "orderId": 1001, "stockCode": "005930", "stockName": "삼성전자", "orderType": "BUY",
        "orderMethod": "LIMIT", "quantity": 10, "filledQuantity": 7, "remainingQuantity": 3,
        "price": 82000, "status": "PARTIAL_FILLED", "orderedAt": "2026-05-17T12:00:00" }
    ],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  },
  "meta": { "traceId": "uuid" }
}
```

---

### STOCK-ORDER-003. 주문 상세 조회
**GET** `/internal/v1/stock/orders/{orderId}`

```json
{
  "success": true,
  "data": {
    "orderId": 1001, "accountId": 2001, "stockCode": "005930", "stockName": "삼성전자",
    "orderType": "BUY", "orderMethod": "LIMIT", "quantity": 10, "filledQuantity": 7,
    "remainingQuantity": 3, "price": 82000, "averageExecutionPrice": 81950,
    "status": "PARTIAL_FILLED", "orderedAt": "2026-05-17T12:00:00", "updatedAt": "2026-05-17T12:03:00"
  },
  "meta": { "traceId": "uuid" }
}
```
> `ORDER_003` (주문 없음)

---

### STOCK-ORDER-004. 주문 취소
**POST** `/internal/v1/stock/orders/{orderId}/cancel`
> Write API

```json
{
  "success": true,
  "data": {
    "orderId": 1001, "status": "CANCELLED", "cancelledQuantity": 8,
    "filledQuantity": 2, "remainingQuantity": 0, "cancelledAt": "2026-05-17T12:10:00"
  },
  "meta": { "traceId": "uuid" }
}
```
> `ORDER_003` / `ORDER_004` (이미 체결/취소된 주문)

---

### STOCK-EXECUTION-001. 체결 조회
**GET** `/internal/v1/stock/accounts/{accountId}/executions`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `stockCode` | X | 종목 코드 |
| `fromDate` | X | `YYYY-MM-DD` |
| `toDate` | X | `YYYY-MM-DD` |
| `page` | X | 기본값 0 |
| `size` | X | 기본값 20 |

```json
{
  "success": true,
  "data": {
    "content": [
      { "executionId": 501, "orderId": 1001, "stockCode": "005930", "stockName": "삼성전자",
        "executedPrice": 82000, "executedQuantity": 5, "executionAmount": 410000, "executedAt": "2026-05-17T12:01:00" }
    ],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  },
  "meta": { "traceId": "uuid" }
}
```

---

### STOCK-HOLDING-001. 보유 종목 조회
**GET** `/internal/v1/stock/accounts/{accountId}/holdings`

```json
{
  "success": true,
  "data": {
    "content": [
      { "stockCode": "005930", "stockName": "삼성전자", "quantity": 20, "averagePrice": 78000,
        "currentPrice": 82000, "evaluationAmount": 1640000, "unrealizedProfit": 80000, "profitRate": 5.12 }
    ]
  },
  "meta": { "traceId": "uuid" }
}
```

---

### STOCK-RETURN-001. 수익률 조회
**GET** `/internal/v1/stock/accounts/{accountId}/returns`

```json
{
  "success": true,
  "data": { "accountId": 2001, "dailyReturnRate": 1.5, "monthlyReturnRate": 7.3, "yearlyReturnRate": 18.1 },
  "meta": { "traceId": "uuid" }
}
```

---

## Phase 5 추가 예정 API (Saga 연동용 인바운드)

> transaction-server → stock-server 호출. 아직 api-stock-server.md 미반영.

### STOCK-CASH-001. 예수금 입금
**POST** `/internal/v1/stock/cash/deposit`

```json
// Request
{ "accountId": 2001, "amount": 1000000 }

// Response 200
{ "success": true, "data": { "accountId": 2001, "cashBalance": 4000000 }, "meta": { "traceId": "uuid" } }
```

### STOCK-CASH-002. 예수금 출금
**POST** `/internal/v1/stock/cash/withdraw`

```json
// Request
{ "accountId": 2001, "amount": 500000 }

// Response 200
{ "success": true, "data": { "accountId": 2001, "cashBalance": 3500000 }, "meta": { "traceId": "uuid" } }
```
> `TRANSFER_002` (잔액 부족)

---

## 참고: 서비스 흐름

```
Client → service-backend (8080, /api/v1)
           → transaction-server (8083, /baas/v1)
               → stock-server (8082, /internal/v1/stock)  ← 우리 서버
```

transaction-server의 `/baas/v1/stocks/...` 엔드포인트는
stock-server `/internal/v1/stock/...` 를 프록시하므로 응답 shape 동일.

---

## Kafka 이벤트 (Phase 5~6)

| Topic | 발행 시점 |
|---|---|
| `stock.cash.deposit.completed` | 예수금 입금 성공 |
| `stock.cash.deposit.failed` | 예수금 입금 실패 |
| `stock.cash.withdraw.completed` | 예수금 출금 성공 |
| `stock.cash.withdraw.failed` | 예수금 출금 실패 |
| `stock.order.completed` | 주문 체결 완료 |
| `stock.order.failed` | 주문 실패 |
