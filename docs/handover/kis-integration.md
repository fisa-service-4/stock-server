# KIS OpenAPI 실시세 연동

> `stock.mock.enabled=false` 일 때 활성화되는 실시세 파이프라인.

---

## 1. Provider 패턴 — 모드 전환의 핵심

`StockPriceProvider` 인터페이스 하나로 Mock과 KIS를 교체한다.

```
StockPriceProvider (interface)
  ├── MockStockPriceProvider   @ConditionalOnProperty(mock.enabled=true)
  └── KisStockPriceProvider    @ConditionalOnProperty(mock.enabled=false)
```

`StockPriceScheduler`와 `OrderService`는 구현체가 아닌 인터페이스만 의존한다.
`application.yaml` 프로퍼티 값 하나가 바뀌면 스프링 IoC가 빈 등록 자체를 바꾸므로
코드 수정 없이 동작이 전환된다.

---

## 2. KIS 토큰 관리 — KisTokenManager

**파일:** `external/kis/auth/KisTokenManager.java`

### Lazy 토큰 캐시 구조

```java
private volatile String cachedToken;       // 스레드 가시성 보장
private volatile LocalDateTime expireAt;

public synchronized String getToken() {    // 동시 재발급 방지
    if (cachedToken == null || LocalDateTime.now().isAfter(expireAt.minusHours(1))) {
        issueToken();
    }
    return cachedToken;
}
```

| 포인트 | 설명 |
|---|---|
| `volatile` | 멀티스레드 환경에서 캐시 필드의 최신값 보장 |
| `synchronized` | 동시 요청 시 토큰 중복 발급 방지 |
| 만료 1시간 전 갱신 | KIS 토큰 유효시간(24h) 만료 전에 선제 재발급 |
| `@PostConstruct` 없음 | 앱 기동 시 발급하지 않고 첫 API 호출 시점에 Lazy 발급 |

### 토큰 발급 흐름

```
POST https://openapivts.koreainvestment.com:29443/oauth2/tokenP

Body: {
  "grant_type": "client_credentials",
  "appkey": ${kis.appKey},
  "appsecret": ${kis.appSecret}
}

Response: {
  "access_token": "...",
  "expires_in": 86400    ← 초 단위, LocalDateTime.now().plusSeconds(86400)으로 변환
}
```

---

## 3. KIS HTTP 클라이언트 — RealKisClient

**파일:** `external/kis/client/RealKisClient.java`

### 공통 헤더

```
Authorization: Bearer {KisTokenManager.getToken()}
appkey:        {kis.appKey}
appsecret:     {kis.appSecret}
custtype:      P                   (개인)
Content-Type:  application/json
```

### 현재가 조회

```
GET {kis.baseUrl}/uapi/domestic-stock/v1/quotations/inquire-price
    ?FID_COND_MRKT_DIV_CODE=J
    &FID_INPUT_ISCD={stockCode}

tr_id: FHKST01010100

주요 응답 필드 (output):
  stck_prpr   현재가
  prdy_vrss   전일 대비
  prdy_ctrt   등락률
```

### 일봉 조회

```
GET {kis.baseUrl}/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice
    ?FID_COND_MRKT_DIV_CODE=J
    &FID_INPUT_ISCD={stockCode}
    &FID_INPUT_DATE_1={fromDate}
    &FID_INPUT_DATE_2={toDate}
    &FID_PERIOD_DIV_CODE=D

tr_id: FHKST03010100
```

`RestTemplate`은 `RestTemplateConfig`에서 빈으로 등록. connect timeout 3초, read timeout 10초.

---

## 4. KisStockPriceProvider — 응답 검증

**파일:** `external/kis/provider/KisStockPriceProvider.java`

```java
@Override
public BigDecimal getCurrentPrice(String stockCode) {
    KisCurrentPriceResponse response = realKisClient.getCurrentPrice(stockCode);

    if (!"0".equals(response.getRtCd()) || response.getOutput() == null) {
        // KIS가 비정상 응답을 내려도 STOCK_002로 변환
        throw new GlobalException(ErrorCode.STOCK_002);
    }

    return kisMapper.toCurrentPrice(response);   // output.stckPrpr → BigDecimal
}
```

`rt_cd == "0"` 이 KIS 정상 응답 코드. 이 외의 값은 종목 없음, 시장 미개설, API 오류 등을 포함한다.

---

## 5. Mock 모드 — MockStockPriceProvider

**파일:** `external/kis/dummy/provider/MockStockPriceProvider.java`

```java
@Override
public BigDecimal getCurrentPrice(String stockCode) {
    // 1. DB에서 가장 최근 종가 조회
    BigDecimal lastPrice = stockPriceHistoryRepository
        .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
        .map(StockPriceHistory::getClosePrice)
        .orElse(FALLBACK_PRICES.getOrDefault(stockCode, BigDecimal.valueOf(50000)));

    // 2. ±0.5% 랜덤 변동 적용
    return mockPriceGenerator.generate(lastPrice);
}
```

**MockPriceGenerator 변동 공식:**

```java
double variationRate = Math.random() * 0.01 - 0.005;  // -0.5% ~ +0.5%
BigDecimal price = lastPrice
    .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(variationRate)))
    .setScale(0, RoundingMode.HALF_UP);
return price.max(BigDecimal.ONE);   // 최소 1원 보장
```

---

## 6. 시세 저장 흐름 (Mock 모드)

```
StockPriceScheduler (fixedDelay=5000ms)
  └── stockMasterRepository.findAll()
      └── for each stock:
            stockPriceProvider.getCurrentPrice(stockCode)   // MockStockPriceProvider
              └── MockPriceGenerator.generate(lastPrice)
            stockPriceHistoryService.recordTick(stockCode, nextPrice)
              └── OHLCV 계산 후 StockPriceHistory INSERT
            Thread.sleep(callDelayMs)   // 종목 간 딜레이 (KIS 호출 제한 대응용)
```

OHLCV 계산 방식:

| 필드 | 값 |
|---|---|
| open | 직전 tick의 closePrice |
| close | 이번 tick의 nextPrice |
| high | max(open, close) |
| low | min(open, close) |
| volume | ThreadLocalRandom(1000, 50001) |
| fluctuationRate | (close - open) / open × 100 |

---

## 7. 설정 파일 구조

```yaml
# application.yaml (Mock 모드)
stock:
  mock:
    enabled: true
    tick-interval: 5000
    call-delay-ms: 0

# application-onpremise.yaml (KIS 모드)
stock:
  mock:
    enabled: false
    call-delay-ms: 100   # KIS API 호출 제한 대응

kis:
  appKey: ${KIS_APP_KEY}
  appSecret: ${KIS_APP_SECRET}
  baseUrl: https://openapivts.koreainvestment.com:29443
```

`KisProperties`는 `@ConfigurationProperties(prefix="kis")`로 바인딩. API Key는 환경변수로만 주입.
