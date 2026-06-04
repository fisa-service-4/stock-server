# Stock Server API 테스트 가이드

> 프론트엔드 개발자용 테스트 레퍼런스  
> Base URL: `http://localhost:8082`  
> 모든 API는 `/internal/v1/stock` 하위에 있습니다.

---

## 테스트 계정 정보

앱 최초 기동 시 자동 생성됩니다.

| 항목 | 값 |
|---|---|
| userId | `1` |
| accountId | `1` |
| 초기 예수금 | `10,000,000원` |
| 계좌명 | `테스트 계좌` |
| 계좌번호 | `1234567890` |
| brokerCode | `243` (한국투자증권) |

### 증권사 코드

| 코드 | 증권사 |
|---|---|
| `243` | 한국투자증권 |
| `247` | NH투자증권 |

### 시드 종목

| stockCode | 종목명 | 기준가 |
|---|---|---|
| `005930` | 삼성전자 | 70,000원 |
| `000660` | SK하이닉스 | 210,000원 |
| `035420` | NAVER | 190,000원 |
| `035720` | 카카오 | 42,000원 |

#### 시드 데이터 구성

| 구분 | 설명 |
|---|---|
| **현재가 (tick)** | 5초마다 ±3% 변동. 체결 엔진 현재가 산출용 |
| **차트 (daily candle)** | 앱 기동 시 최근 30일 영업일치 자동 삽입. `collectedAt=15:30` 기준 |

daily candle은 앱 재기동 시 이미 존재하면 skip (중복 삽입 없음).

---

## 공통 헤더

| 헤더 | 필수 | 설명 |
|---|---|---|
| `X-User-Id` | O | 테스트 계정: `1` |
| `X-Trace-Id` | O | 임의 문자열 (ex: `test-001`) |

---

## 1. 헬스 체크

```bash
curl http://localhost:8082/internal/v1/stock/health
```

**응답**
```json
{ "success": true, "data": { "database": "UP", "server": "UP" } }
```

---

## 2. 종목 검색

```bash
curl -G http://localhost:8082/internal/v1/stock/search \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  --data-urlencode "keyword=삼성"
```

```bash
# 종목코드로도 검색 가능
curl -G http://localhost:8082/internal/v1/stock/search \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  --data-urlencode "keyword=005930"
```

---

## 3. 현재가 조회

> 5초 주기로 가격이 변동됩니다. 이 값이 주문 체결 시 현재가로 사용됩니다.

```bash
curl http://localhost:8082/internal/v1/stock/005930/price \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

**다른 종목**
```bash
# SK하이닉스
curl http://localhost:8082/internal/v1/stock/000660/price -H "X-User-Id: 1" -H "X-Trace-Id: t"

# NAVER
curl http://localhost:8082/internal/v1/stock/035420/price -H "X-User-Id: 1" -H "X-Trace-Id: t"

# 카카오
curl http://localhost:8082/internal/v1/stock/035720/price -H "X-User-Id: 1" -H "X-Trace-Id: t"
```

---

## 4. 차트 조회

> **interval**: `DAILY` / `WEEKLY` / `MONTHLY` 모두 지원.  
> `fromDate` / `toDate` 미입력 시 기본값: 최근 30일.  
> 앱 기동 시 최근 30일 영업일치 daily candle이 자동 삽입되므로 별도 데이터 준비 불필요.

```bash
# 기본 (최근 30일 DAILY)
curl -G http://localhost:8082/internal/v1/stock/005930/charts \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  --data-urlencode "interval=DAILY"
```

```bash
# 기간 직접 지정
curl -G http://localhost:8082/internal/v1/stock/005930/charts \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  --data-urlencode "interval=DAILY" \
  --data-urlencode "fromDate=2026-05-01" \
  --data-urlencode "toDate=2026-05-29"
```

```bash
# 주봉 (각 주의 첫 영업일 기준 캔들)
curl -G http://localhost:8082/internal/v1/stock/005930/charts \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  --data-urlencode "interval=WEEKLY"
```

```bash
# 월봉 (각 월의 첫 영업일 기준 캔들)
curl -G http://localhost:8082/internal/v1/stock/005930/charts \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  --data-urlencode "interval=MONTHLY"
```

**응답 예시**
```json
{
  "success": true,
  "data": {
    "content": [
      { "date": "2026-05-02", "open": 70000.00, "high": 71200.00, "low": 69500.00, "close": 70800.00, "volume": 432000 },
      { "date": "2026-05-03", "open": 70800.00, "high": 72000.00, "low": 70200.00, "close": 71500.00, "volume": 687000 }
    ]
  }
}
```

**데이터 구조**
- 과거 날짜: daily candle (1일 1건, `collectedAt=15:30`)
- 오늘: 최신 tick 1건이 당일 캔들로 표시됨 (장중 부분 캔들)
- 주말/공휴일: 데이터 없음 (영업일만 존재)

---

## 5. 계좌 조회

```bash
curl http://localhost:8082/internal/v1/stock/accounts \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

**응답 예시**
```json
{
  "success": true,
  "data": {
    "content": [
      { "accountId": 1, "accountNumber": "1234567890", "accountName": "테스트 계좌", "bankCode": "243" }
    ]
  }
}
```

---

## 6. 예수금 조회

```bash
curl http://localhost:8082/internal/v1/stock/accounts/1/cash-balance \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

**응답 예시**
```json
{
  "success": true,
  "data": { "accountId": 1, "cashBalance": 10000000.00, "availableBalance": 10000000.00 }
}
```

---

## 7. 계좌 유효성 검증

> **온프레미스 내부 전용 API.** transaction-server가 이체 Saga 진행 전 입금 대상 계좌를 검증할 때 호출합니다.  
> `toBankCode`는 증권사 식별 코드입니다. `243` (한국투자증권) / `247` (NH투자증권) 두 값만 유효합니다.  
> 시드 계좌의 brokerCode는 `243`입니다.

### 7-1. 정상 계좌 검증

```bash
curl -X POST http://localhost:8082/internal/v1/stock/accounts/validate \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-001" \
  -d '{
    "toBankCode": "243",
    "toAccountNumber": "1234567890"
  }'
```

**응답 예시**
```json
{
  "success": true,
  "data": {
    "validYn": true,
    "status": "ACTIVE"
  }
}
```

### 7-2. 에러 케이스

```bash
# 존재하지 않는 계좌
curl -X POST http://localhost:8082/internal/v1/stock/accounts/validate \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-001" \
  -d '{ "toBankCode": "KIS", "toAccountNumber": "0000000000" }'
```

```json
{ "success": false, "error": { "code": "ACCOUNT_001", "message": "계좌 없음" } }
```

---

## 8. 주문 생성

### 8-1. 매수 — 시장가 (MARKET)

```bash
curl -X POST http://localhost:8082/internal/v1/stock/accounts/1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "MARKET",
    "quantity": 5
  }'
```

### 8-2. 매수 — 지정가 (LIMIT)

> 현재가 이상으로 지정하면 즉시 체결됩니다. 현재가보다 낮게 지정하면 `REQUESTED` 상태로 대기합니다.

```bash
curl -X POST http://localhost:8082/internal/v1/stock/accounts/1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "quantity": 3,
    "price": 80000
  }'
```

### 8-3. 매도 — 시장가

> 매수 체결 후 보유 수량이 있어야 성공합니다.

```bash
curl -X POST http://localhost:8082/internal/v1/stock/accounts/1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001" \
  -d '{
    "stockCode": "005930",
    "orderType": "SELL",
    "orderMethod": "MARKET",
    "quantity": 2
  }'
```

**주문 생성 응답 예시**
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "MARKET",
    "quantity": 5,
    "filledQuantity": 5,
    "remainingQuantity": 0,
    "status": "FILLED",
    "orderedAt": "2026-05-27T10:00:00"
  }
}
```

**에러 케이스**
```json
// 예수금 부족
{ "success": false, "error": { "code": "ORDER_001", "message": "주문 가능 금액이 부족합니다" } }

// 보유 수량 부족
{ "success": false, "error": { "code": "ORDER_002", "message": "보유 수량이 부족합니다" } }
```

---

## 9. 주문 취소

> `REQUESTED` 상태인 주문만 취소 가능합니다. 이미 체결(`FILLED`)된 주문은 취소 불가합니다.

```bash
# orderId는 주문 생성 응답에서 확인
curl -X POST http://localhost:8082/internal/v1/stock/orders/1/cancel \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

---

## 10. 주문 목록 조회

```bash
# 전체 목록
curl "http://localhost:8082/internal/v1/stock/accounts/1/orders" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

```bash
# 상태 필터 (REQUESTED / FILLED / CANCELLED / FAILED)
curl "http://localhost:8082/internal/v1/stock/accounts/1/orders?status=FILLED" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

```bash
# 매수/매도 필터 + 페이지네이션
curl "http://localhost:8082/internal/v1/stock/accounts/1/orders?orderType=BUY&page=0&size=10" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

---

## 11. 주문 상세 조회

```bash
# orderId=1 상세 조회
curl http://localhost:8082/internal/v1/stock/orders/1 \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

**응답 예시** (`averageExecutionPrice` 포함)
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "accountId": 1,
    "stockCode": "005930",
    "stockName": "삼성전자",
    "orderType": "BUY",
    "orderMethod": "MARKET",
    "quantity": 5,
    "filledQuantity": 5,
    "remainingQuantity": 0,
    "averageExecutionPrice": 70350.00,
    "status": "FILLED",
    "orderedAt": "2026-05-27T10:00:00"
  }
}
```

---

## 12. 체결 내역 조회

```bash
# 전체 체결 내역
curl "http://localhost:8082/internal/v1/stock/accounts/1/executions" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

```bash
# 종목 필터
curl "http://localhost:8082/internal/v1/stock/accounts/1/executions?stockCode=005930" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

```bash
# 날짜 범위 필터
curl "http://localhost:8082/internal/v1/stock/accounts/1/executions?fromDate=2026-05-27&toDate=2026-05-27" \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

---

## 13. 보유종목 조회

> 매수 체결 후 확인하세요. 보유종목이 없으면 빈 배열(`[]`)로 응답합니다.

```bash
curl http://localhost:8082/internal/v1/stock/accounts/1/holdings \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

**응답 예시**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "stockCode": "005930",
        "stockName": "삼성전자",
        "quantity": 5,
        "averagePrice": 70350.00,
        "currentPrice": 71200.00,
        "evaluationAmount": 356000.00,
        "unrealizedProfit": 4250.00,
        "profitRate": 1.21
      }
    ]
  }
}
```

---

## 14. 수익률 조회

> `dailyReturnRate`는 전날 스냅샷이 없으면 `null`로 반환됩니다.

```bash
curl http://localhost:8082/internal/v1/stock/accounts/1/returns \
  -H "X-User-Id: 1" \
  -H "X-Trace-Id: test-001"
```

**응답 예시**
```json
{
  "success": true,
  "data": {
    "accountId": 1,
    "totalReturnRate": 1.21,
    "dailyReturnRate": null
  }
}
```

---

## 추천 테스트 순서

처음 테스트할 때는 아래 순서로 진행하면 전체 흐름을 확인할 수 있습니다.

```
1.  헬스 체크              → 서버 연결 확인
2.  종목 검색              → 삼성전자 검색
3.  현재가 조회            → 005930 현재가 확인
4.  예수금 조회            → 초기 10,000,000원 확인
5.  계좌 유효성 검증       → 입금 대상 계좌 검증 (온프레미스 내부용)
6.  주문 생성 (매수)       → 삼성전자 5주 시장가 매수
7.  주문 상세 조회         → FILLED 상태 + 평균체결가 확인
8.  체결 내역 조회         → 체결 기록 확인
9.  예수금 재조회          → 매수금액만큼 감소 확인
10. 보유종목 조회          → 삼성전자 5주 + 실시간 평가금액 확인
11. 수익률 조회            → totalReturnRate 확인
12. 주문 생성 (매도)       → 보유 수량 내에서 매도
13. 보유종목 재조회        → 수량 감소 확인
```

---

## Swagger UI

전체 API 스펙은 Swagger에서 확인할 수 있습니다.

```
http://localhost:8082/swagger-ui/index.html
```

---

## Windows curl 참고

Windows PowerShell에서는 `uuidgen` 대신 아래를 사용하세요.

```powershell
# 주문 생성 예시
curl.exe -X POST http://localhost:8082/internal/v1/stock/accounts/1/orders `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1" `
  -H "X-Trace-Id: test-001" `
  -d '{\"stockCode\":\"005930\",\"orderType\":\"BUY\",\"orderMethod\":\"MARKET\",\"quantity\":5}'
```
