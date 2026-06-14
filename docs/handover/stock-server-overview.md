# stock-server 개요

## 1. MSA에서의 위치

```
Client
  └── service-backend (8080, /api/v1)
        └── transaction-server (8083, /baas/v1)
              └── stock-server (8082, /internal/v1/stock)  ← 여기
```

stock-server는 외부 클라이언트와 직접 통신하지 않는다. transaction-server의 프록시 역할로 호출되며,
JWT 인증 없이 `X-User-Id` / `X-Trace-Id` 헤더 기반 내부 통신만 허용한다.

---

## 2. 운영 모드

`application.yaml`의 단일 프로퍼티로 시세 수집 방식 전체가 전환된다.

```yaml
stock:
  mock:
    enabled: true   # true=Mock 모드, false=KIS 실시세 모드
    tick-interval: 5000
```

### Mock 모드 (`mock.enabled=true`)

| 컴포넌트 | 활성 여부 | 설명 |
|---|---|---|
| `MockStockPriceProvider` | ✅ | 전일 종가 ±0.5% 랜덤 변동가 생성 |
| `StockPriceScheduler` | ✅ | tick-interval 간격으로 전 종목 시세 저장 |
| `KisStockPriceProvider` | ❌ | 빈 미등록 |

`@ConditionalOnProperty(prefix="stock.mock", name="enabled", havingValue="true")` 어노테이션으로
스프링 빈 자체가 등록되지 않는다. 프로파일 분기가 아닌 빈 등록 조건 분기.

### KIS 실시세 모드 (`mock.enabled=false`)

| 컴포넌트 | 활성 여부 | 설명 |
|---|---|---|
| `KisStockPriceProvider` | ✅ | KIS OpenAPI 현재가 조회 |
| `StockPriceScheduler` | ❌ | 빈 미등록 |
| `MockStockPriceProvider` | ❌ | 빈 미등록 |

KIS 모드에서는 스케줄러가 없으므로 시세는 API 호출 시점에 on-demand로 조회된다.
`application-onpremise.yaml` 프로파일에서 활성화.

---

## 3. 스케줄러 목록

매일 자정 00:00 KST에 두 스케줄러가 동시 실행된다.

| 스케줄러 | 실행 주기 | 활성 조건 | 역할 |
|---|---|---|---|
| `StockPriceScheduler` | tick-interval (기본 5초) | mock.enabled=true | 전 종목 시세 수집·저장 |
| `PendingOrderScheduler` | match-interval (기본 5초) | 항상 | REQUESTED 상태 LIMIT 주문 재시도 |
| `PortfolioSnapshotScheduler` | 매일 00:00 KST | 항상 | ACTIVE 계좌 포트폴리오 스냅샷 |
| `ReconciliationScheduler` | 매일 00:00 KST | 항상 | 원장 정합성 5종 검증 |

---

## 4. 핵심 테이블

| 테이블 | 역할 |
|---|---|
| `SECURITIES_ACCOUNT` | 증권 계좌, 예수금(`cash_balance`) |
| `STOCK_MASTER` | 종목 마스터 (stockCode, stockName, market) |
| `STOCK_PRICE_HISTORY` | 시세 이력 (OHLCV + fluctuationRate) |
| `STOCK_ORDER` | 주문 원장 (idempotency_key UNIQUE) |
| `STOCK_EXECUTION` | 체결 원장 |
| `STOCK_HOLDING` | 보유 종목 (UNIQUE: accountId + stockCode) |
| `STOCK_PORTFOLIO_SNAPSHOT` | 일별 포트폴리오 스냅샷 |
| `ORDER_MODIFICATION_HISTORY` | 주문 취소/변경 이력 (CLOB) |
| `RECONCILIATION_RESULT` | 일일 정합성 검증 결과 |

---

## 5. 공통 규칙

### 요청 헤더

| 헤더 | 설명 | 없을 때 |
|---|---|---|
| `X-User-Id` | 사용자 식별 ID | 공통 에러 응답 |
| `X-Trace-Id` | 요청 추적 ID | UUID 자동 생성 |

`TraceLoggingFilter`가 요청 진입 시 `X-Trace-Id`를 MDC에 저장한다.
모든 로그는 `MDC.get("traceId")`를 prefix로 출력한다.

### 응답 포맷

```json
// 성공
{ "success": true, "data": {}, "meta": { "traceId": "uuid" } }

// 실패
{ "success": false, "error": { "code": "ORDER_001", "message": "..." }, "meta": { "traceId": "uuid" } }
```

---

## 6. 패키지 구조 요약

```
com.fisa.stock (또는 com.stock)
├── domain/
│   ├── account/      SecuritiesAccount, 예수금, 계좌 검증
│   ├── stock/        StockMaster, StockPriceHistory, 시세 서비스, 스케줄러
│   ├── order/        StockOrder, 주문 생성/취소, 체결 엔진
│   ├── execution/    StockExecution, 체결 조회
│   ├── holding/      StockHolding, 보유 종목, 수익률
│   ├── portfolio/    StockPortfolioSnapshot, 스냅샷 스케줄러
│   └── reconciliation/ ReconciliationResult, 정합성 검증 스케줄러
│
├── external/
│   └── kis/
│       ├── auth/     KisTokenManager (토큰 캐시)
│       ├── client/   RealKisClient (HTTP)
│       ├── provider/ StockPriceProvider (인터페이스), KisStockPriceProvider
│       └── dummy/    MockStockPriceProvider, MockPriceGenerator
│
└── global/
    ├── config/       SecurityConfig, SwaggerConfig, RestTemplateConfig, DataInitializer
    ├── exception/    ErrorCode, GlobalException, GlobalExceptionHandler
    ├── response/     ApiResponse<T>
    └── filter/       TraceLoggingFilter (MDC traceId 주입)
```
