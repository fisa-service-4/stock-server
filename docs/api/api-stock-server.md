# stock-server API 명세

> Port: `8082`

## Base URL

```text
/internal/v1
```
---

## 공통 내부 요청 Header

```http
X-User-Id: {userId}
X-Trace-Id: {uuid}
```

- Transaction Server가 사용자 인증 수행
- Core Server는 검증 완료된 내부 요청만 처리
- JWT / Bearer Token 직접 처리하지 않음

---

## STOCK API

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | /stocks/search | 종목 검색 | Internal |
| GET | /stocks/{stockCode}/price | 종목 현재가 조회 | Internal |
| GET | /stocks/{stockCode}/chart | 종목 차트 조회 | Internal |

---

## ORDER API

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | /orders | 주식 주문 생성 | Internal |
| POST | /orders/{orderId}/cancel | 주문 취소 | Internal |
| GET | /orders | 주문 내역 조회 | Internal |
| GET | /orders/{orderId} | 주문 상세 조회 | Internal |

---

## EXECUTION API

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | /executions | 체결 내역 조회 | Internal |

---

## HOLDING API

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | /holdings | 보유 종목 조회 | Internal |
| GET | /holdings/returns | 수익률 조회 | Internal |


# 증권 Core API 명세서

## 담당 범위

- Stock
- Order
- Execution
- Holding
- Portfolio
- Account (주문용)

---

# 공통 규칙

## Base URL

```text
/internal/v1
```

---

## 내부 인증 방식

```http
X-User-Id: {userId}
X-Trace-Id: {uuid}
```

- Transaction Server가 사용자 인증 수행
- Core Server는 검증 완료된 내부 요청만 처리
- JWT / Bearer Token 직접 처리하지 않음

---

## 거래 API 추가 헤더

```http
Pin-Token: {pinToken}
Idempotency-Key: {uuid}
```

- PIN 검증 완료 토큰
- 중복 주문 방지

---

# 공통 응답 포맷

## Success

```json
{
  "success": true,
  "data": {},
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## Error

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지"
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

# 1. STOCK API

---

## 1-1. 종목 검색

### GET /internal/v1/stocks/search

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Query Parameter

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| keyword | String | 종목명 또는 종목코드 |

### Response

```json
{
  "success": true,
  "data": [
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "market": "KOSPI"
    },
    {
      "stockCode": "035720",
      "stockName": "카카오",
      "market": "KOSDAQ"
    }
  ],
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 1-2. 종목 현재가 조회

### GET /internal/v1/stocks/{stockCode}/price

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": {
    "stockCode": "005930",
    "stockName": "삼성전자",
    "currentPrice": 82000,
    "changeRate": -1.2,
    "changeAmount": -1000,
    "volume": 12345678,
    "updatedAt": "2026-05-17T12:00:00"
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 1-3. 종목 차트 조회

### GET /internal/v1/stocks/{stockCode}/chart

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Query Parameter

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| interval | String | DAILY / WEEKLY / MONTHLY |
| from | LocalDate | 시작일 |
| to | LocalDate | 종료일 |

### Response

```json
{
  "success": true,
  "data": {
    "stockCode": "005930",
    "candles": [
      {
        "date": "2026-05-17",
        "open": 81000,
        "high": 82500,
        "low": 80500,
        "close": 82000,
        "volume": 12345678
      }
    ]
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

# 2. ORDER API

---

## 2-1. 주식 주문 생성

### POST /internal/v1/orders

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |
| Pin-Token | PIN 검증 토큰 |
| Idempotency-Key | 중복 주문 방지 |

### Request

```json
{
  "stockCode": "005930",
  "orderType": "BUY",
  "orderMethod": "LIMIT",
  "quantity": 10,
  "price": 82000
}
```

### 주문 정책

```text
- MARKET 주문 시 price는 null
- LIMIT 주문 시 price 필수
- BUY 주문 시 예수금 검증
- SELL 주문 시 보유 수량 검증
```

### Response

```json
{
  "success": true,
  "data": {
    "orderId": 1001,
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "quantity": 10,
    "price": 82000,
    "filledQuantity": 0,
    "remainingQuantity": 10,
    "status": "REQUESTED",
    "orderedAt": "2026-05-17T12:00:00"
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

### Error Response

#### 주문 가능 금액 부족

```json
{
  "success": false,
  "error": {
    "code": "ORDER_001",
    "message": "주문 가능 금액이 부족합니다."
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

#### 보유 수량 부족

```json
{
  "success": false,
  "error": {
    "code": "ORDER_002",
    "message": "보유 수량이 부족합니다."
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 2-2. 주문 취소

### POST /internal/v1/orders/{orderId}/cancel

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |
| Pin-Token | PIN 검증 토큰 |
| Idempotency-Key | 중복 요청 방지 |

### 주문 취소 정책

```text
- 미체결 수량에 대해서만 취소 가능
- 이미 체결된 수량은 취소되지 않음
- FILLED 상태 주문은 취소 불가
```

### Response

```json
{
  "success": true,
  "data": {
    "orderId": 1001,
    "status": "CANCELLED",
    "cancelledQuantity": 8,
    "filledQuantity": 2,
    "remainingQuantity": 0,
    "cancelledAt": "2026-05-17T12:10:00"
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 2-3. 주문 내역 조회

### GET /internal/v1/orders

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Query Parameter

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| status | String | REQUESTED / PARTIAL_FILLED / FILLED / CANCELLED |
| orderType | String | BUY / SELL |
| page | Integer | 페이지 |
| size | Integer | 페이지 크기 |

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "orderId": 1001,
        "stockCode": "005930",
        "stockName": "삼성전자",
        "orderType": "BUY",
        "orderMethod": "LIMIT",
        "orderPrice": 82000,
        "orderQuantity": 10,
        "filledQuantity": 10,
        "remainingQuantity": 0,
        "status": "FILLED",
        "orderedAt": "2026-05-17T12:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 2-4. 주문 상세 조회

### GET /internal/v1/orders/{orderId}

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": {
    "orderId": 1001,
    "stockCode": "005930",
    "stockName": "삼성전자",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "orderPrice": 82000,
    "orderQuantity": 10,
    "filledQuantity": 10,
    "remainingQuantity": 0,
    "averageExecutionPrice": 81950,
    "status": "FILLED",
    "orderedAt": "2026-05-17T12:00:00"
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

# 3. EXECUTION API

---

## 3-1. 체결 내역 조회

### GET /internal/v1/executions

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Query Parameter

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| stockCode | String | 종목코드 |
| from | LocalDate | 시작일 |
| to | LocalDate | 종료일 |
| page | Integer | 페이지 |
| size | Integer | 페이지 크기 |

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "executionId": 501,
        "orderId": 1001,
        "stockCode": "005930",
        "stockName": "삼성전자",
        "executionType": "BUY",
        "executedPrice": 82000,
        "executedQuantity": 10,
        "executionAmount": 820000,
        "executedAt": "2026-05-17T12:01:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

# 4. HOLDING API

---

## 4-1. 보유 종목 조회

### GET /internal/v1/holdings

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": {
    "totalAsset": 15430000,
    "totalProfit": 1230000,
    "totalProfitRate": 8.67,
    "holdings": [
      {
        "stockCode": "005930",
        "stockName": "삼성전자",
        "holdingQuantity": 50,
        "averagePurchasePrice": 70000,
        "currentPrice": 73500,
        "evaluatedAmount": 3675000,
        "unrealizedProfit": 175000,
        "profitRate": 5.0
      },
      {
        "stockCode": "035720",
        "stockName": "카카오",
        "holdingQuantity": 30,
        "averagePurchasePrice": 48000,
        "currentPrice": 45200,
        "evaluatedAmount": 1356000,
        "unrealizedProfit": -84000,
        "profitRate": -5.83
      }
    ]
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 4-2. 수익률 조회

### GET /internal/v1/holdings/returns

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": {
    "dailyReturnRate": 1.82,
    "monthlyReturnRate": 8.67,
    "yearlyReturnRate": 21.34,
    "totalReturnRate": 15.28
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

# 5. PORTFOLIO API

---

## 5-1. 포트폴리오 조회

### GET /internal/v1/portfolio

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": {
    "totalAsset": 24500000,
    "stockAsset": 18500000,
    "cashAsset": 6000000,
    "totalProfit": 2300000,
    "totalProfitRate": 10.35,
    "distribution": [
      {
        "assetType": "STOCK",
        "amount": 18500000,
        "ratio": 75.5
      },
      {
        "assetType": "CASH",
        "amount": 6000000,
        "ratio": 24.5
      }
    ]
  },
  "meta": {
    "traceId": "uuid"
  }
}
```

---

# 6. ACCOUNT API (주문용)

---

## 6-1. 주문 가능 계좌 조회

### GET /internal/v1/stocks/accounts

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": [
    {
      "accountId": 1,
      "accountNumber": "123-45-67890",
      "brokerName": "삼성증권",
      "accountType": "위탁"
    }
  ],
  "meta": {
    "traceId": "uuid"
  }
}
```

---

## 6-2. 예수금 조회

### GET /internal/v1/stocks/cash-balance

### Header

| 이름 | 설명 |
| --- | --- |
| X-User-Id | 사용자 ID |
| X-Trace-Id | 요청 추적 ID |

### Response

```json
{
  "success": true,
  "data": {
    "accountId": 1,
    "availableCash": 5200000,
    "withdrawableAmount": 5000000
  },
  "meta": {
    "traceId": "uuid"
  }
}
```