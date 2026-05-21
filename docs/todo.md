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
- [x] `GET /internal/v1/stocks/search?keyword=` — 검색 결과 없을 시 STOCK_001 (404)
- [x] `AccountService` / `AccountController`
- [x] `GET /internal/v1/stocks/accounts` — 계좌 없을 시 ACCOUNT_001 (404)
- [x] `GET /internal/v1/stocks/cash-balance` — 계좌 없을 시 ACCOUNT_001 (404)

---

## ✅ Phase 2 — 시세 시스템 구축

- [x] `application.yaml`: `stock.mock.enabled: true` / `stock.mock.tick-interval: 5000` 추가
- [x] `MockPriceGenerator`: 순수 계산 클래스, ±3% 랜덤 변동률 적용
- [x] `MockStockPriceProvider`: `@ConditionalOnProperty(stock.mock.enabled)`, fallback 기준가 Map, `getNextPrice()` / `getFallbackPrice()`
- [x] `StockPriceHistoryService`: `recordTick()` / `initializeIfAbsent()` / `getCurrentPrice()` / `getChart()`
- [x] `DataInitializer`: `MockStockPriceProvider` optional 주입, 앱 기동 시 종목별 초기 시세 1건 삽입
- [x] `StockPriceScheduler`: `@ConditionalOnProperty`, `fixedDelayString` 5초 주기, 종목별 독립 try-catch
- [x] `StockPriceResponse` DTO (currentPrice / changeRate / changeAmount / volume / updatedAt)
- [x] `StockChartResponse` DTO (stockCode / candles[], Candle.from() 정적 팩토리)
- [x] `GET /internal/v1/stocks/{stockCode}/price` 동작 확인
- [x] `GET /internal/v1/stocks/{stockCode}/chart?interval=&from=&to=` 동작 확인
- [x] 에러 코드 STOCK_001 / STOCK_002 / STOCK_003 적용 확인

---

## 🔲 Phase 3 — 주문 / 체결 엔진 구축

- [ ] `StockOrder` Service / Controller
- [ ] `Idempotency-Key` 헤더 처리 (중복 주문 방지)
- [ ] `Pin-Token` 헤더 존재 검증
- [ ] 매수 검증: `cash_balance >= quantity × price` (ORDER_001)
- [ ] 매도 검증: `holding_quantity >= quantity` (ORDER_002)
- [ ] `POST /internal/v1/orders` 동작 확인
- [ ] 체결 엔진 `@Transactional` 단일 처리:
  - [ ] `StockExecution` 생성
  - [ ] `StockHolding` upsert (매수: 평균단가 재계산 / 매도: 수량 감소)
  - [ ] `SecuritiesAccount.cashBalance` 갱신
  - [ ] `StockOrder` 상태 변경 (FILLED)
- [ ] `StockExecution` Service
- [ ] `GET /internal/v1/executions` 동작 확인
- [ ] `POST /internal/v1/orders/{orderId}/cancel` 동작 확인 (이력 저장 포함)
- [ ] `GET /internal/v1/orders` / `GET /internal/v1/orders/{orderId}` 동작 확인
- [ ] LIMIT 주문 조건 체결 동작 확인

---

## 🔲 Phase 4 — 보유종목 / 수익률 / 포트폴리오

- [ ] `StockHolding` Service / Controller
- [ ] `GET /internal/v1/holdings` (실시간 평가금액 계산) 동작 확인
- [ ] `GET /internal/v1/holdings/returns` (totalReturnRate 실시간) 동작 확인
- [ ] `StockPortfolioSnapshot` Service
- [ ] 포트폴리오 스냅샷 스케줄러 (일 1회)
- [ ] `GET /internal/v1/portfolio` 동작 확인
- [ ] 에러 코드 HOLDING_001 적용 확인

---

## 🔲 Phase 5 — 예수금 + Kafka 연동

- [ ] build.gradle: `spring-kafka` 의존성 추가
- [ ] application.yaml: Kafka bootstrap-servers 설정
- [ ] `POST /internal/v1/cash/deposit` 동작 확인
- [ ] `POST /internal/v1/cash/withdraw` 동작 확인 (잔액 부족 검증 포함)
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
