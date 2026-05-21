PHASE 4 — 보유종목 읽기 / 수익률 / 포트폴리오 구축

목표:
- 보유종목 조회 (실시간 평가금액 계산)
- 수익률 조회
- 포트폴리오 조회
- STOCK_PORTFOLIO_SNAPSHOT 생성

전략:
STOCK_HOLDING 쓰기는 Phase 3 체결 엔진에서 완료됨
Phase 4는 읽기 사이드 및 집계 API에 집중

────────────────────────────────────

4-1. STOCK_HOLDING 읽기 구현

평가금액 계산 방식:
DB에 저장된 값이 아닌 서비스 레이어에서 실시간 계산

이유:
evaluated_amount를 DB에 저장하면 시세 변동 즉시 stale

계산:
평가금액 = 현재가 (STOCK_PRICE_HISTORY 최신) × 보유수량
미실현손익 = 평가금액 - 총매입금액
수익률 = 미실현손익 / 총매입금액 × 100

────────────────────────────────────

4-2. 보유종목 조회 API 구현

API:
GET /internal/v1/holdings

응답:
- totalAsset        ← 총 평가금액 합계
- totalProfit       ← 총 미실현손익
- totalProfitRate   ← 총 수익률
- holdings: [
    stockCode, stockName,
    holdingQuantity, averagePurchasePrice,
    currentPrice, evaluatedAmount,
    unrealizedProfit, profitRate
  ]

────────────────────────────────────

4-3. 수익률 API 구현

API:
GET /internal/v1/holdings/returns

응답:
- totalReturnRate   ← 실시간 계산 가능
- dailyReturnRate   ← STOCK_PORTFOLIO_SNAPSHOT 이력 필요 (초기 0 또는 null)
- monthlyReturnRate ← 동일
- yearlyReturnRate  ← 동일

주의:
초기에는 SNAPSHOT 이력이 없으므로 daily/monthly/yearly는 null 반환
totalReturnRate만 실시간 계산

────────────────────────────────────

4-4. STOCK_PORTFOLIO_SNAPSHOT 구축

목적:
일별 포트폴리오 집계 이력 저장
→ 수익률 계산 / 시계열 분석용

구조:
snapshot_id / user_id / total_asset / stock_asset / cash_asset /
total_profit / total_profit_rate / snapshot_date / created_at

스케줄러:
일 1회 @Scheduled 또는 수동 트리거로 스냅샷 저장

────────────────────────────────────

4-5. 포트폴리오 조회 API 구현

API:
GET /internal/v1/portfolio

응답:
- totalAsset     ← cash_balance + 주식 평가금액 합계
- stockAsset     ← 주식 평가금액 합계
- cashAsset      ← SECURITIES_ACCOUNT.cash_balance
- totalProfit    ← 총 미실현손익
- totalProfitRate
- distribution: [
    { assetType: STOCK, amount, ratio },
    { assetType: CASH, amount, ratio }
  ]

────────────────────────────────────

PHASE 4 완료 기준:

✅ GET /internal/v1/holdings 실시간 평가금액 계산 동작
✅ 총 평가자산 / 총 손익 / 총 수익률 계산 정확성 확인
✅ GET /internal/v1/holdings/returns totalReturnRate 동작
✅ GET /internal/v1/portfolio 현금 + 주식 집계 동작
✅ STOCK_PORTFOLIO_SNAPSHOT 엔티티 및 스케줄러 동작 확인
✅ 에러 코드 HOLDING_001 적용
