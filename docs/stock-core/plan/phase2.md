PHASE 2 — 시세 시스템 구축

목표:
- 현재가 조회
- 차트 조회
- 더미 시세 생성 (5초 주기 스케줄러)

전략:
REST 기반 시세 조회 구현
WebSocket 실시간 확장은 Phase 4 이후에 별도 진행

────────────────────────────────────

2-1. STOCK_PRICE_HISTORY 구축

기능:
- 시세 틱 저장 (5초마다 1건)
- 현재가 및 차트 조회용 이력 관리

데이터 저장 방식:
스케줄러가 생성하는 틱 단위로 저장
→ traded_date: 거래일 (DATE)
→ collected_at: 틱 수집 시각 (TIMESTAMP) ← 차트 조회 기준 컬럼

Oracle 타입 주의:
- DECIMAL(18,2) → NUMBER(18,2)
- ENUM → VARCHAR2

────────────────────────────────────

2-2. 초기 가격 시드

목표:
앱 기동 시 STOCK_MASTER 각 종목에 초기 가격 1건 삽입
→ 시작부터 GET /price 정상 응답 가능

DataInitializer 또는 @PostConstruct 기반으로 처리

────────────────────────────────────

2-3. 더미 시세 Generator 구현

전략:
5초마다 종목별 랜덤 가격 변동 (±3% 이내)

예:
73,500 → 73,700 → 73,400

────────────────────────────────────

2-4. Scheduler 구현

예:
StockPriceScheduler (@Scheduled, fixedDelay = 5000)

역할:
- 각 종목 현재가 기준 랜덤 변동
- STOCK_PRICE_HISTORY 저장
- open / high / low / close / volume 계산 (단순화 허용)

────────────────────────────────────

2-5. 현재가 API 구현

API:
GET /internal/v1/stocks/{stockCode}/price

조회 방식:
collected_at 기준 최신 row 조회

응답:
- stockCode
- stockName
- currentPrice
- changeRate
- changeAmount
- volume
- updatedAt

────────────────────────────────────

2-6. 차트 API 구현

API:
GET /internal/v1/stocks/{stockCode}/chart?interval={}&from={}&to={}

조회 방식:
from ~ to 범위 내 collected_at 기준 row 조회

응답:
- stockCode
- candles: [{ date, open, high, low, close, volume }]

────────────────────────────────────

WebSocket 확장에 대해:

이 Phase에서는 WebSocket 관련 코드를 생성하지 않음
external/kis/websocket/ 디렉터리 생성 보류

Phase 4 완료 후 필요 시:
- KisWebSocketClient 추가
- KIS WebSocket 프레임 파서 추가 (예: 005930^093015^80500^2^1200)

────────────────────────────────────

PHASE 2 완료 기준:

✅ STOCK_PRICE_HISTORY 엔티티 및 저장 동작
✅ 앱 기동 시 초기 가격 시드 삽입 확인
✅ 5초 주기 시세 스케줄러 동작 확인
✅ GET /internal/v1/stocks/{stockCode}/price 정상 응답
✅ GET /internal/v1/stocks/{stockCode}/chart 정상 응답
✅ 에러 코드 STOCK_001 / STOCK_002 / STOCK_003 적용
