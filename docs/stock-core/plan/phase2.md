PHASE 2 — 시세 시스템 구축

목표:
- 현재가 조회
- 차트 조회
- 더미 시세 생성 (5초 주기 스케줄러)

전략:
REST 기반 시세 조회 구현
WebSocket 실시간 확장은 Phase 4 이후에 별도 진행

────────────────────────────────────

✅ PHASE 2 완료 (2026-05-22)

구현 브랜치 및 이슈:
- feat/#11 : StockPriceHistory Service 기반 구축
- feat/#12 : MockStockPriceProvider 기반 시세 생성
- feat/#13 : StockPriceScheduler 5초 주기 시세 수집
- feat/#14 : 차트 조회 API 구현

────────────────────────────────────

2-1. application.yaml 설정 추가

stock:
  mock:
    enabled: true
    tick-interval: 5000

- mock.enabled: MockStockPriceProvider / StockPriceScheduler 빈 활성화 여부
- mock.enabled=false 시 Mock 빈 미등록 → 실제 KIS 연동 시 자동 전환 가능

────────────────────────────────────

2-2. MockPriceGenerator 구현

패키지: external/kis/dummy/generator/

역할:
순수 계산만 담당. 외부 의존 없음.

generate(BigDecimal lastPrice):
- Math.random() × 0.06 − 0.03 변동률 적용
- setScale(2, RoundingMode.HALF_UP) 반환
- 최솟값 1원 보장 (max(generatedPrice, 1))

────────────────────────────────────

2-3. MockStockPriceProvider 구현

패키지: external/kis/dummy/provider/
조건: @ConditionalOnProperty(prefix="stock.mock", name="enabled", havingValue="true")

fallback 기준가 (DataInitializer 초기 시드와 동일):
- 005930 삼성전자   → 70,000원
- 000660 SK하이닉스 → 210,000원
- 035420 NAVER     → 190,000원
- 035720 카카오    → 42,000원

getNextPrice(String stockCode, BigDecimal lastPrice):
- lastPrice가 null이면 fallback Map에서 기준가 사용
- 있으면 lastPrice 기반 MockPriceGenerator.generate() 호출
- DEBUG 로그 출력

getFallbackPrice(String stockCode):
- DataInitializer에서 초기 시세 삽입 시 호출용

────────────────────────────────────

2-4. StockPriceHistoryService 구현

패키지: domain/stock/service/

recordTick(String stockCode, BigDecimal prevClose, BigDecimal newClose):
- OHLCV 계산: open=prevClose, close=newClose, high=max, low=min
- fluctuationRate = (close-open)/open × 100 (prevClose=0 방어)
- volume = ThreadLocalRandom.nextLong(1000, 50001)
- StockPriceHistory 저장 후 INFO 로그

initializeIfAbsent(String stockCode, BigDecimal initialPrice):
- findTopByStockCodeOrderByCollectedAtDesc 로 존재 여부 확인
- 없으면 open=close=high=low=initialPrice, volume=0, fluctuationRate=0 저장

getCurrentPrice(String stockCode):
- stockMasterRepository.findById → 없으면 STOCK_001 + WARN
- findTopByStockCodeOrderByCollectedAtDesc → 없으면 STOCK_002 + WARN
- StockPriceResponse.of(master, history) 반환 + INFO

getChart(String stockCode, LocalDateTime from, LocalDateTime to):
- stockMasterRepository.findById → 없으면 STOCK_001 + WARN
- findByStockCodeAndCollectedAtBetweenOrderByCollectedAtAsc → 빈 리스트면 STOCK_003 + WARN
- List<Candle> 변환 후 StockChartResponse 반환 + INFO (count 포함)

────────────────────────────────────

2-5. DataInitializer 수정

StockPriceHistoryService 필드 추가 (RequiredArgsConstructor)
MockStockPriceProvider 필드 @Autowired(required=false) 추가

initStockPrices():
- mockStockPriceProvider == null이면 early return (mock.enabled=false 대응)
- stockMasterRepository.findAll().forEach → initializeIfAbsent 호출

────────────────────────────────────

2-6. StockPriceScheduler 구현

패키지: domain/stock/scheduler/
조건: @ConditionalOnProperty(prefix="stock.mock", name="enabled", havingValue="true")
주기: @Scheduled(fixedDelayString = "${stock.mock.tick-interval}")

흐름 (종목당):
1. findTopByStockCodeOrderByCollectedAtDesc → prevClose 획득
2. mockStockPriceProvider.getNextPrice(stockCode, prevClose) → nextPrice
3. stockPriceHistoryService.recordTick(stockCode, prevClose, nextPrice)
4. 종목별 독립 try-catch (한 종목 실패가 다른 종목 차단 방지)

INFO 로그: 스케줄러 시작/완료 + 처리된 종목 수

────────────────────────────────────

2-7. DTO

StockPriceResponse (domain/stock/dto/response/):
- stockCode, stockName, currentPrice (closePrice), changeRate (fluctuationRate)
- changeAmount (close - open), volume, updatedAt (collectedAt)
- static of(StockMaster, StockPriceHistory) 정적 팩토리

StockChartResponse (domain/stock/dto/response/):
- stockCode, List<Candle>
- Candle: timestamp (collectedAt), open, high, low, close, volume
- static Candle.from(StockPriceHistory) 정적 팩토리
- timestamp 타입: LocalDateTime (5초 tick 데이터이므로 날짜 단위 불충분)

────────────────────────────────────

2-8. StockController 수정

엔드포인트 2개 추가:

GET /{stockCode}/price
- PathVariable stockCode
- @Operation Swagger 문서
- INFO 로그: "현재가 조회 stockCode={}"
- → stockPriceHistoryService.getCurrentPrice()

GET /{stockCode}/chart
- PathVariable stockCode
- RequestParam interval (수신만, Phase 2에서 집계 미사용)
- RequestParam @DateTimeFormat(ISO.DATE) LocalDate from / to
- from.atStartOfDay() / to.atTime(23, 59, 59) 변환 후 서비스 호출
- INFO 로그: "차트 조회 stockCode={} interval={} from={} to={}"
- → stockPriceHistoryService.getChart()

────────────────────────────────────

PHASE 2 완료 기준:

✅ application.yaml mock 설정 추가
✅ MockPriceGenerator / MockStockPriceProvider 구현
✅ StockPriceHistoryService 구현 (4개 메서드)
✅ DataInitializer 수정 → 앱 기동 시 초기 시세 4건 삽입
✅ StockPriceScheduler 구현 → 5초마다 tick 저장 확인
✅ StockPriceResponse / StockChartResponse DTO 생성
✅ GET /internal/v1/stocks/{stockCode}/price 정상 응답
✅ GET /internal/v1/stocks/{stockCode}/chart 정상 응답
✅ 에러 코드 STOCK_001 / STOCK_002 / STOCK_003 적용
✅ Swagger @Operation 문서 작성 완료

────────────────────────────────────

다음 단계: PHASE 3 — 주문 / 체결 엔진 구축
→ docs/stock-core/plan/phase3.md 참조
