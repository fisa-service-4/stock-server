# stock-server TODO

---

## ✅ 완료

### 개발 환경 / 공통

- [x] docs/stock-core/plan/phase1~6 개발 계획 문서 작성
- [x] build.gradle: postgresql → ojdbc11 드라이버 교체
- [x] application.yaml: Oracle datasource / JPA dialect / port 8082 / 인코딩 설정
- [x] application.yaml: `defer-datasource-initialization: true` / `sql.init.mode: always`
- [x] `StockServerApplication`: `@EnableScheduling` / `@EnableJpaAuditing` 추가
- [x] `SecurityConfig`: CSRF 비활성화, 내부 서버 전체 허용
- [x] `ApiResponse<T>`: 공통 응답 포맷 `{ success, data, error, meta.traceId }`
- [x] `ErrorCode`: error-code.md 기준 에러 코드 enum
- [x] `GlobalException` / `GlobalExceptionHandler`
- [x] `HealthController`: `GET /internal/v1/stock/health` (Oracle DB 연결 확인)
- [x] `SwaggerConfig`: `@OpenAPIDefinition` 설정 (제목 / 서버 URL)
- [x] Oracle 컨테이너 기동 확인 (docker-compose, XEPDB1)

---

### 공통 인프라 정비

- [x] `HeaderConstants`: `X-Trace-Id` / `X-User-Id` 문자열 상수 중앙 관리
- [x] 전체 컨트롤러 내부 헤더 `required=false` 통일 (헤더 누락 시 공통 에러 응답 보장)
- [x] `GlobalExceptionHandler`: `MDC.get("traceId")` 기반 처리 통일, 미처리 예외 ERROR 로그 추가
- [x] `TraceLoggingFilter`: X-Trace-Id MDC 저장, 없으면 UUID 생성(MDC 전용), 요청 시작/종료 INFO 로그
- [x] `docs/logging/stock-logging-policy.md`: INFO/WARN/ERROR/DEBUG 레벨 기준, traceId 정책, 금지 로그 목록 문서화

---

### Phase 1 전 — DB 스키마 선생성

- [x] `BaseEntity` (createdAt / updatedAt, JPA Auditing)
- [x] 도메인 Enum 생성
  - [x] `MarketType` (KOSPI / KOSDAQ / NASDAQ / NYSE / ETF)
  - [x] `AccountStatus` (ACTIVE / LOCKED / CLOSED)
  - [x] `OrderType` / `OrderMethod` / `OrderStatus` / `OrderedBy`
  - [x] `ModificationType` / `ModifiedBy`
- [x] `StockMaster` 엔티티 (STOCK_MASTER)
- [x] `SecuritiesAccount` 엔티티 (SECURITIES_ACCOUNT)
- [x] `StockPriceHistory` 엔티티 (STOCK_PRICE_HISTORY)
- [x] `StockOrder` 엔티티 (STOCK_ORDER, idempotency_key UNIQUE)
- [x] `StockExecution` 엔티티 (STOCK_EXECUTION)
- [x] `StockHolding` 엔티티 (STOCK_HOLDING, UNIQUE account+stock)
- [x] `StockPortfolioSnapshot` 엔티티 (STOCK_PORTFOLIO_SNAPSHOT)
- [x] `OrderModificationHistory` 엔티티 (ORDER_MODIFICATION_HISTORY, CLOB)
- [x] Repository 인터페이스 8개 생성
- [x] 앱 기동 후 8개 테이블 생성 확인
- [x] UNIQUE 제약조건 확인 (account_number / idempotency_key / holding account+stock)

---

### Phase 1 — 기본 도메인 구축

- [x] `KisClient` 인터페이스 + `DummyKisClient` 구현체
- [x] `KisCurrentPriceResponse` (rt_cd / msg_cd / output envelope 구조)
- [x] `KisMapper` (외부 DTO → BigDecimal 변환)
- [x] `data.sql`: 종목 4개 시드 (삼성전자 / SK하이닉스 / NAVER / 카카오, Oracle MERGE INTO)
- [x] `DataInitializer`: 테스트 계좌 1개 자동 생성 (userId=1, 예수금 10,000,000원)
- [x] `StockService` / `StockController`
- [x] `GET /internal/v1/stock/search?keyword=` — 검색 결과 없을 시 STOCK_001 (404)
- [x] `AccountService` / `AccountController`
- [x] `GET /internal/v1/stock/accounts` — 계좌 없을 시 ACCOUNT_001 (404)
- [x] `GET /internal/v1/stock/accounts/{accountId}/cash-balance` — 계좌 없을 시 ACCOUNT_001 (404)

---

### Phase 2 — 시세 시스템 구축

- [x] `application.yaml`: `stock.mock.enabled: true` / `stock.mock.tick-interval: 5000` 추가
- [x] `MockPriceGenerator`: 순수 계산 클래스, ±3% 랜덤 변동률 적용
- [x] `MockStockPriceProvider`: `@ConditionalOnProperty(stock.mock.enabled)`, fallback 기준가 Map, `getNextPrice()` / `getFallbackPrice()`
- [x] `StockPriceHistoryService`: `recordTick()` / `initializeIfAbsent()` / `getCurrentPrice()` / `getChart()`
- [x] `DataInitializer`: `MockStockPriceProvider` optional 주입, 앱 기동 시 종목별 초기 시세 1건 삽입
- [x] `StockPriceScheduler`: `@ConditionalOnProperty`, `fixedDelayString` 5초 주기, 종목별 독립 try-catch
- [x] `StockPriceResponse` DTO (currentPrice / changeRate / changeAmount / volume / updatedAt)
- [x] `StockChartResponse` DTO (content[], CandleItem.from() 정적 팩토리)
- [x] `GET /internal/v1/stock/{stockCode}/price` 동작 확인
- [x] `GET /internal/v1/stock/{stockCode}/charts?interval=&fromDate=&toDate=` 동작 확인
- [x] 에러 코드 STOCK_001 / STOCK_002 / STOCK_003 적용 확인

---

### Refactor #19 — API 명세 정합성 정비

- [x] `docs/api-compact.md` 생성 (stock-server 전용 API 요약, 매 세션 전체 API 문서 재독 불필요)
- [x] `CLAUDE.md` 참조 문서 섹션에 `api-compact.md` / `todo.md` 추가
- [x] URL 단수형 통일: `/internal/v1/stocks` → `/internal/v1/stock` (StockController / AccountController)
- [x] 차트 경로 수정: `/{stockCode}/chart` → `/{stockCode}/charts`
- [x] 차트 파라미터 수정: `from` / `to` → `fromDate` / `toDate`
- [x] 예수금 경로 수정: `/cash-balance` → `/accounts/{accountId}/cash-balance`
- [x] `StockSearchResponse`: `currentPrice` / `changeRate` 필드 추가
- [x] `StockChartResponse`: `stockCode` 제거, `candles` → `content`, `timestamp(LocalDateTime)` → `date(LocalDate)`
- [x] `AccountResponse`: `brokerName` → `bankCode`
- [x] `CashBalanceResponse`: `availableCash` → `cashBalance`, `withdrawableAmount` → `availableBalance`
- [x] `AccountValidator` 생성 (`domain/account/validator/`) — `validateOwner(userId, accountId)` : 존재(ACCOUNT_001) + 소유자(ACCOUNT_002) 검증 후 엔티티 반환
- [x] `AccountService.getCashBalance`: `AccountValidator` 적용, 시그니처 `(userId, accountId)`로 변경

---

## 🔲 Phase 3 — 주문 / 체결 엔진 구축

### ✅ Issue #21 — 주문 생성 / 즉시 체결 엔진 / 취소 (완료)

- [x] `OrderCreateRequest` / `OrderCreateResponse` / `OrderCancelResponse` DTO
- [x] `OrderService` — `createOrder` / `cancelOrder` / 즉시 체결 엔진 `tryExecute`
  - [x] `Pin-Token` 헤더 존재 검증 (non-null, non-empty) → VALID_002
  - [x] `Idempotency-Key` 헤더 처리 (동일 키 재요청 시 기존 주문 반환)
  - [x] `AccountValidator.validateOwner()` 적용 (계좌 소유자 확인)
  - [x] 매수 검증: `cashBalance >= quantity × price(LIMIT) / currentPrice(MARKET)` → ORDER_001
  - [x] 매도 검증: `holdingQuantity >= quantity` → ORDER_002
  - [x] 체결 엔진 `@Transactional` 단일 처리:
    - [x] `StockExecution` 생성
    - [x] `StockHolding` upsert (매수: 평균단가 재계산 / 매도: 수량 감소, 0이면 삭제)
    - [x] `SecuritiesAccount.cashBalance` 갱신 (매수: withdraw / 매도: deposit)
    - [x] `StockOrder` 상태 변경 (FILLED)
  - [x] MARKET 주문: 현재가 즉시 체결
  - [x] LIMIT 주문: 조건 충족 시 즉시 체결, 미충족 시 REQUESTED 유지
  - [x] `cancelOrder`: REQUESTED 상태만 취소 가능, `OrderModificationHistory` 저장
- [x] `OrderController`
  - [x] `POST /internal/v1/stock/accounts/{accountId}/orders` → 201 Created
  - [x] `POST /internal/v1/stock/orders/{orderId}/cancel` → 200 OK

### ✅ Issue #22 — 주문 / 체결 조회 (완료)

- [x] `OrderListItemResponse` / `OrderDetailResponse` DTO (`stockName`, `averageExecutionPrice`, `accountId` 포함)
- [x] `OrderQueryService` — `getOrders(userId, accountId, status, orderType, page, size)` / `getOrderDetail(userId, orderId)`
- [x] `OrderController` GET 엔드포인트 추가
  - [x] `GET /internal/v1/stock/accounts/{accountId}/orders` (status / orderType 필터, 페이지네이션)
  - [x] `GET /internal/v1/stock/orders/{orderId}` (상세, `averageExecutionPrice` 포함)
- [x] `StockExecutionRepository` JPQL 서브쿼리 추가 (accountId → StockOrder IN 서브쿼리)
- [x] `ExecutionResponse` DTO (`stockName` 포함, StockMaster 조회)
- [x] `ExecutionService` — `getExecutions(userId, accountId, stockCode, fromDate, toDate, page, size)`
- [x] `ExecutionController` — `GET /internal/v1/stock/accounts/{accountId}/executions`

---

## 🔲 Phase 4 — 보유종목 / 수익률 / 포트폴리오

### ✅ Issue #25 — 보유종목 조회 + 수익률 조회 (완료)

- [x] `HoldingResponse` / `HoldingReturnResponse` DTO
- [x] `HoldingService` — `getHoldings(userId, accountId)` / `getReturns(userId, accountId)`
  - [x] `StockPriceHistoryRepository.findLatestByStockCodes()` 배치 조회 (N+1 제거)
  - [x] 실시간 평가금액 / unrealizedProfit / profitRate 계산
  - [x] holdings empty → 200 OK 빈 리스트 반환
  - [x] `totalReturnRate` 실시간 계산
  - [x] `dailyReturnRate` 어제 snapshot 기반 (없으면 null)
- [x] `HoldingController`
  - [x] `GET /internal/v1/stock/accounts/{accountId}/holdings` → `200 OK`
  - [x] `GET /internal/v1/stock/accounts/{accountId}/returns` → `200 OK`

### ✅ Issue #26 — 포트폴리오 스냅샷 + 스케줄러 (완료)

- [x] `SecuritiesAccountRepository.findAllByAccountStatus()` 추가
- [x] `StockPortfolioSnapshotRepository.existsByUserIdAndSnapshotDate()` 추가 (중복 방지)
- [x] `PortfolioSnapshotService` — `takeSnapshot(userId, accountId)`
  - [x] priceMap 배치 조회 패턴 적용 (N+1 없음)
  - [x] stockAsset / cashAsset / totalProfit / totalProfitRate 계산
  - [x] 중복 저장 방지 (`existsByUserIdAndSnapshotDate` guard)
- [x] `PortfolioSnapshotScheduler` — `@Scheduled(cron = "0 0 0 * * *")` 매일 자정
  - [x] ACTIVE 계좌만 처리
  - [x] 계좌별 독립 try-catch (1개 실패가 전체 차단 방지)

---

## 🔲 Phase 5 — 예수금 + Kafka 연동

- [ ] build.gradle: `spring-kafka` 의존성 추가
- [ ] application.yaml: Kafka bootstrap-servers 설정
- [ ] `POST /internal/v1/stock/accounts/{accountId}/cash/deposit` 동작 확인
- [ ] `POST /internal/v1/stock/accounts/{accountId}/cash/withdraw` 동작 확인 (잔액 부족 검증 포함)
- [ ] Kafka 이벤트 발행 확인 (kafka-ui):
  - [ ] `stock.cash.deposit.completed`
  - [ ] `stock.cash.deposit.failed`
  - [ ] `stock.cash.withdraw.completed`
  - [ ] `stock.cash.withdraw.failed`
  - [ ] `stock.order.completed`
  - [ ] `stock.order.failed`
- [ ] 원자성 한계 문서화 (Phase 6 필요성 명시)

---

## 🔲 Phase 6 — Outbox / Event 안정화

- [ ] `OutboxEvent` 엔티티 (OUTBOX_EVENT 테이블)
- [ ] 예수금 변경 + `OutboxEvent` INSERT 동일 트랜잭션 처리
- [ ] `OutboxRelayWorker`: `@Scheduled` 5초 주기 PENDING → PUBLISHED
- [ ] Kafka 발행 실패 시 FAILED 상태 전환 확인
- [ ] `retry_count < 3` 재시도 동작 확인
- [ ] Kafka 장애 후 복구 시 자동 재발행 확인
