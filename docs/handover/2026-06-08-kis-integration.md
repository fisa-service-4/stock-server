# 인수인계: KIS OpenAPI 실시세 연동

> 작성일: 2026-06-08  
> 작성자: Claude (세션 컨텍스트 압축 전 인수인계)  
> 관련 계획: `docs/stock-core/plan/kis-integration.md`  
> 관련 TODO: `docs/todo.md` — "KIS OpenAPI 실시세 연동" 섹션

---

## 현재 완료된 내용

Phase 1~5 전체 구현 완료. **KIS 연동 작업은 설계만 완료, 코드 작성 미시작.**

세션에서 확정된 내용:
- Provider 패턴 채택 (Scheduler를 2개로 분리하지 않음)
- `recordTick` 시그니처를 `(stockCode, newClose)`로 변경 — prevClose는 내부 조회 (Option B)
- Mock 모드: DB 2회 조회 (동일 데이터, 로컬 개발 전용이라 허용)
- KIS 모드: DB 1회 조회 (prevClose만)
- `KisTokenManager` — `@PostConstruct` 없음, Lazy 방식 (KIS 장애 시 서버 기동 차단 방지)

---

## 남은 작업

### Issue A — `refactor`: Provider 패턴 도입 (선행 필수)

파일 5개 수정 — KIS 크레덴셜 없이 진행 가능:

| # | 작업 | 파일 |
|---|---|---|
| 1 | 인터페이스 신규 생성 | `external/kis/provider/StockPriceProvider.java` |
| 2 | Mock 구현체 수정 | `external/kis/dummy/provider/MockStockPriceProvider.java` |
| 3 | recordTick 시그니처 변경 | `domain/stock/service/StockPriceHistoryService.java` |
| 4 | Scheduler 리팩터 | `domain/stock/scheduler/StockPriceScheduler.java` |
| 5 | @ConditionalOnProperty 추가 | `external/kis/dummy/DummyKisClient.java` |

### Issue B — `feat`: KIS 실연동 (Issue A 머지 후)

파일 9개 신규/수정 — KIS 앱 키 필요:

| # | 작업 | 파일 | 신규/수정 |
|---|---|---|---|
| 1 | RestTemplate 빈 | `global/config/RestTemplateConfig.java` | 신규 |
| 2 | 설정 바인딩 | `external/kis/config/KisProperties.java` | 신규 |
| 3 | 토큰 요청 DTO | `external/kis/dto/KisTokenRequest.java` | 신규 |
| 4 | 토큰 응답 DTO | `external/kis/dto/KisTokenResponse.java` | 신규 |
| 5 | 토큰 캐시 | `external/kis/auth/KisTokenManager.java` | 신규 |
| 6 | HTTP 클라이언트 | `external/kis/client/RealKisClient.java` | 신규 |
| 7 | KIS Provider | `external/kis/provider/KisStockPriceProvider.java` | 신규 |
| 8 | 로컬 설정 | `src/main/resources/application.yaml` | 수정 |
| 9 | 온프레미스 설정 | `src/main/resources/application-onpremise.yaml` | 수정 |

---

## 구현 순서

```
[Issue A — 선행, KIS 크레덴셜 불필요]
1. StockPriceProvider.java 인터페이스 생성
2. MockStockPriceProvider.java 수정
   - implements StockPriceProvider 추가
   - StockPriceHistoryRepository 필드 추가
   - getCurrentPrice() 메서드 추가
   - getNextPrice() 메서드 제거
   - getFallbackPrice() 유지
3. StockPriceHistoryService.java 수정
   - recordTick(stockCode, prevClose, newClose) → recordTick(stockCode, newClose)
   - 메서드 내부에서 prevClose 직접 조회
4. StockPriceScheduler.java 수정
   - @ConditionalOnProperty 제거
   - StockPriceProvider 주입 (인터페이스)
   - StockPriceHistoryRepository 제거
   - MockStockPriceProvider 직접 의존 제거
   - @Value("${kis.call-delay-ms:100}") callDelayMs 추가
   - for-each → for-loop (Thread.sleep 사용 위해)
5. DummyKisClient.java 수정
   - @ConditionalOnProperty(mock.enabled=true, matchIfMissing=true) 추가
↓
[Mock 모드 기동 후 정상 동작 확인 후 머지]

[Issue B — KIS 크레덴셜 필요]
6. RestTemplateConfig.java 생성 (connect=3s, read=10s)
7. KisProperties.java 생성
8. application.yaml에 kis: 블록 추가
9. KisTokenRequest.java, KisTokenResponse.java 생성
10. KisTokenManager.java 생성 (Lazy, synchronized)
11. RealKisClient.java 생성
12. KisStockPriceProvider.java 생성 (rt_cd 검증)
13. application-onpremise.yaml 수정
↓
[Real 모드 기동 후 KIS 응답 확인]
```

---

## 위험 요소

| 위험 | 내용 | 대응 |
|---|---|---|
| **빌드 오류** | `recordTick` 시그니처 변경 시 기존 호출부 컴파일 오류 가능 | `StockPriceScheduler`만 호출 중이므로 동시에 수정하면 됨 |
| **Mock 모드 기동 실패** | `MockStockPriceProvider`에 `StockPriceHistoryRepository` 추가 후 순환 의존 가능성 | 없음 — 단방향 의존이므로 무방 |
| **DummyKisClient 충돌** | `@ConditionalOnProperty` 없이 real 모드 전환 시 `NoUniqueBeanDefinitionException` | Issue A에서 반드시 추가 필요 |
| **KIS Token @PostConstruct** | 서버 기동 시 KIS 다운이면 기동 실패 | Lazy 방식 사용 — 절대 @PostConstruct 추가 금지 |
| **rt_cd 검증 누락** | KIS가 HTTP 200이어도 `rt_cd != "0"` 이면 오류 | `KisStockPriceProvider`에서 반드시 검증 |
| **API Key 하드코딩** | 보안 규칙 위반 | 반드시 `${KIS_APP_KEY}` 환경변수로만 주입 |
| **TPS 초과** | KIS 초당 20건 제한 | `callDelayMs=100ms` → 초당 10건 (종목 4개면 0.4초 사이클) |

---

## 관련 파일

### 수정 대상 (현재 코드 확인 필요)

| 파일 | 경로 | 주요 내용 |
|---|---|---|
| `StockPriceScheduler.java` | `domain/stock/scheduler/` | `@ConditionalOnProperty(mock.enabled=true)` 고정, MockStockPriceProvider 직접 의존 |
| `StockPriceHistoryService.java` | `domain/stock/service/` | `recordTick(String, BigDecimal, BigDecimal)` — 3인자 |
| `MockStockPriceProvider.java` | `external/kis/dummy/provider/` | `getNextPrice()` / `getFallbackPrice()`, Provider 미구현 |
| `DummyKisClient.java` | `external/kis/dummy/` | `@ConditionalOnProperty` 없음 |

### 재사용 가능 (수정 불필요)

| 파일 | 경로 | 재사용 이유 |
|---|---|---|
| `KisClient.java` | `external/kis/client/` | 인터페이스 그대로 사용 |
| `KisCurrentPriceResponse.java` | `external/kis/dto/` | KIS envelope 구조 이미 정확히 모델링됨 |
| `KisMapper.java` | `external/kis/mapper/` | `toCurrentPrice()` 그대로 사용 |
| `MockPriceGenerator.java` | `external/kis/dummy/generator/` | `initDailyCandles()`에서 계속 사용 |
| `DataInitializer.java` | `global/config/` | `mockStockPriceProvider.getFallbackPrice()` 계속 호출 — 수정 불필요 |

---

## 다음 세션 시작 시 해야 할 첫 작업

### 1단계: 기존 코드 재확인 (5분)

```
Read 순서:
1. docs/todo.md — KIS OpenAPI 섹션 체크
2. domain/stock/scheduler/StockPriceScheduler.java
3. domain/stock/service/StockPriceHistoryService.java
4. external/kis/dummy/provider/MockStockPriceProvider.java
```

### 2단계: Issue A 구현 시작

이슈 생성 후:
```
브랜치: refactor/#XX-stock-price-provider-pattern
```

첫 파일 작성:
```java
// external/kis/provider/StockPriceProvider.java
package com.stock.external.kis.provider;

import java.math.BigDecimal;

public interface StockPriceProvider {
    BigDecimal getCurrentPrice(String stockCode);
}
```

이후 위 "구현 순서" 2→3→4→5 순으로 진행.

### 3단계: 빌드 확인

```bash
./gradlew build
```

`recordTick` 시그니처 변경 후 컴파일 오류 없는지 반드시 확인.

### 4단계: Mock 모드 기동 테스트

```bash
./gradlew bootRun
# 또는 IDE에서 기동
# stock.mock.enabled=true 유지
```

- 5초마다 `[StockPriceHistoryService] 시세 tick 저장` 로그 확인
- `GET /internal/v1/stock/005930/price` 응답 확인

---

## 설계 결정 로그 (다음 세션 참고용)

| 결정 | 채택 방안 | 이유 |
|---|---|---|
| Scheduler 분리 여부 | 분리 안 함 (Provider 패턴) | Scheduler 로직 동일, Provider 구현체만 교체 |
| `recordTick` prevClose 처리 | 내부 조회 (Option B) | Scheduler에서 prevClose 조회 책임 제거, Mock 2회 조회 허용 |
| 토큰 초기화 방식 | Lazy (첫 호출 시) | KIS 다운 시 서버 기동 차단 방지 |
| HTTP 클라이언트 | RestTemplate | WebFlux 의존성 불필요, Servlet stack |
| TPS 처리 | `Thread.sleep(callDelayMs)` | 100ms → 초당 10건, `@Scheduled(fixedDelay)` 조합 |
