# 일일 정합성 검증 (Reconciliation)

> 매일 자정 자동 실행되는 증권 원장 정합성 검증 시스템.
> 결과는 `RECONCILIATION_RESULT` 테이블에 누적 저장된다.

---

## 1. 개요

체결 엔진이 `@Transactional`로 원자성을 보장하더라도, 배포 직전 장애·DB 세션 끊김·비정상 종료 등의 상황에서
원장 데이터 간 불일치가 발생할 수 있다. 정합성 검증은 이를 일 단위로 탐지한다.

```
ReconciliationScheduler (매일 00:00 KST)
  ├── checkOrderExecutionConsistency()  주문-체결 수량 정합성
  ├── checkHoldingConsistency()         체결-보유 수량 정합성
  ├── checkOrderStatusConsistency()     주문 상태-수량 정합성
  ├── checkCashBalance()                예수금 음수 탐지
  └── checkHoldingIntegrity()           보유수량 음수 탐지
        ↓
  ReconciliationResult 저장 (status: OK / WARN / ERROR)
```

---

## 2. 검증 항목 상세

### 2-1. 주문-체결 수량 정합성 (checkOrderExecutionConsistency)

**대상:** `FILLED`, `PARTIAL_FILLED`, `REQUESTED` 상태 주문

**검증 조건:**
```sql
-- JPQL: COALESCE 서브쿼리
filledQuantity != COALESCE(SUM(execution.executedQuantity), 0)
OR filledQuantity > orderQuantity
```

체결 테이블에 기록된 실제 체결 수량 합계와 주문 테이블의 `filledQuantity`가 다를 때 불일치로 판정.

**오류 수준:** WARN

---

### 2-2. 체결-보유 수량 정합성 (checkHoldingConsistency)

가장 복잡한 검증. 체결 이력의 순수량과 현재 보유 수량을 전체 비교한다.

**알고리즘:**

```
Step 1: STOCK_EXECUTION에서 (accountId, stockCode) 별 순수량 집계
        BUY   → +executedQuantity
        SELL  → -executedQuantity
        결과를 Map<"accountId:stockCode", Long>으로 구성

Step 2: STOCK_HOLDING 전체 로드 → 동일 키 Map 구성

Step 3: 두 Map 교차 비교
        execNet != holdingQty         → 불일치 (+1)
        executionMap에 없는 holding   → 체결 기록 없는 보유 (+1)
```

**오류 수준:** ERROR (예수금 음수와 동급)

이 검증이 ERROR를 내면 누군가 체결 기록 없이 보유가 생겼거나, 체결됐는데 보유에 반영이 안 된 것이다.
즉시 수동 확인이 필요한 심각한 상태.

---

### 2-3. 주문 상태-수량 정합성 (checkOrderStatusConsistency)

상태값과 수량 필드가 일관성 있는지 확인한다.

**검증 조건:**
```
FILLED:
  filledQuantity != orderQuantity    → 전체 체결인데 수량이 다름
  remainingQuantity != 0             → 전체 체결인데 잔량이 남음

PARTIAL_FILLED:
  filledQuantity == 0                → 부분 체결인데 체결 수량 없음
  filledQuantity == orderQuantity    → 사실 전체 체결인데 상태가 틀림

REQUESTED:
  filledQuantity != 0                → 미체결인데 체결 수량 있음
  remainingQuantity != orderQuantity → 미체결인데 잔량이 주문량과 다름
```

**오류 수준:** WARN

---

### 2-4. 예수금 음수 탐지 (checkCashBalance)

```sql
SELECT * FROM SECURITIES_ACCOUNT WHERE cash_balance < 0
```

예수금이 음수라는 것은 출금 시 잔액 검증을 통과한 뒤 다른 트랜잭션이 동시에 차감했거나,
체결 엔진에 버그가 있다는 의미다. 원장 손상 수준의 심각한 오류.

**오류 수준:** ERROR, 로그 레벨 `log.error`

---

### 2-5. 보유수량 음수 탐지 (checkHoldingIntegrity)

```sql
SELECT * FROM STOCK_HOLDING WHERE holding_quantity < 0
```

매도 수량 검증(`ORDER_002`)을 통과했는데 수량이 음수가 됐다면 동시성 문제 또는 체결 엔진 버그.

**오류 수준:** ERROR, 로그 레벨 `log.error`

---

## 3. 상태 결정 로직

```java
ReconciliationStatus status;

if (hasError || cashMismatch > 0 || holdingMismatch > 0) {
    status = ERROR;   // 스케줄러 예외 발생 OR 원장 손상 수준 불일치
} else if (total > 0) {
    status = WARN;    // 그 외 수량/상태 불일치
} else {
    status = OK;
}
```

| 상태 | 조건 | 대응 |
|---|---|---|
| `OK` | 모든 검증 통과 | 정상 |
| `WARN` | 주문-체결 또는 상태-수량 불일치 | 다음 영업일 내 확인 |
| `ERROR` | cashMismatch/holdingMismatch > 0 또는 예외 발생 | 즉시 수동 확인 필요 |

---

## 4. 스케줄러 설계 포인트

**파일:** `domain/reconciliation/scheduler/ReconciliationScheduler.java`

### 독립 try-catch

5개 검증이 각각 `try-catch`로 감싸져 있다.
1번 검증에서 예외가 발생해도 2~5번은 계속 실행된다.

```java
try {
    orderMismatch = reconciliationService.checkOrderExecutionConsistency();
} catch (Exception e) {
    log.error("[정합성] 주문-체결 수량 검증 실패", e);
    hasError = true;    // 상태는 ERROR로 기록되지만 다음 검증은 계속
}
// ... (나머지 4개도 동일)
```

검증 자체가 실패했을 때도 그 사실을 `hasError=true`와 `status=ERROR`로 기록하므로
"검증을 못 했다"는 정보도 이력에 남는다.

### 실행 시간 측정

```java
LocalDateTime startedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
// ... 검증 실행 ...
LocalDateTime completedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
long durationMs = Duration.between(startedAt, completedAt).toMillis();
```

`RECONCILIATION_RESULT`에 `startedAt`, `completedAt`, `durationMs`가 함께 저장된다.
검증 소요시간이 갑자기 길어지면 테이블 풀스캔이나 인덱스 누락의 신호가 될 수 있다.

---

## 5. ReconciliationResult 테이블

```
RECONCILIATION_RESULT
  run_date                DATE          검증 기준 날짜
  order_mismatch_count    NUMBER        주문-체결 불일치 건수
  holding_mismatch_count  NUMBER        체결-보유 불일치 건수
  status_mismatch_count   NUMBER        상태-수량 불일치 건수
  cash_mismatch_count     NUMBER        예수금 음수 계좌 수
  holding_integrity_count NUMBER        보유수량 음수 종목 수
  total_mismatch_count    NUMBER        위 5개 합계
  status                  VARCHAR2(10)  OK / WARN / ERROR
  started_at              TIMESTAMP     검증 시작 시각
  completed_at            TIMESTAMP     검증 완료 시각
  duration_ms             NUMBER        소요 시간 (ms)
  created_at              TIMESTAMP     레코드 생성 시각 (@PrePersist)
```

`BaseEntity`를 사용하지 않고 `@PrePersist`로 직접 `created_at`을 관리한다.
불변 감사 레코드 성격이므로 `updated_at`은 없다.

---

## 6. PortfolioSnapshotScheduler와의 실행 순서

두 스케줄러 모두 `cron = "0 0 0 * * *", zone = "Asia/Seoul"`로 설정되어 자정에 동시 트리거된다.
스프링의 `@Scheduled`는 기본적으로 단일 스레드 풀에서 실행되므로 실제 실행 순서는 보장되지 않는다.

포트폴리오 스냅샷이 완료된 후 정합성 검증이 실행되어야 당일 스냅샷 데이터도 검증 대상에 포함되지만,
현재 구현에서는 순서 보장 로직이 없다. 운영상 문제가 되면 두 스케줄러의 cron을 분리(예: 스냅샷 23:55, 검증 00:05)하는 방법을 고려할 수 있다.
