# 온프레미스 Saga 설계 문서

> 관련 서버: `transaction-server` · `bank-server` · `stock-server`
> 최종 수정: 2026-06-03
> 공유 대상: 온프레미스 3개 서버 전체

---

# 1. 전체 구조

```
service-backend
    ↓  REST
transaction-server  ← Saga Orchestrator (8083, /baas/v1)
    ↓  OpenFeign                ↓  OpenFeign
bank-server (8081)        stock-server (8082)
/internal/v1/bank         /internal/v1/stock
```

**원칙:**
- service-backend는 transaction-server만 호출한다
- bank-server / stock-server는 transaction-server 내부에서만 호출한다 (외부 직접 접근 금지)
- Saga는 **동기 REST 호출** 기반으로 구동된다

---

# 2. 핵심 설계 결정 및 이유

## 2-1. Saga 구동을 Kafka가 아닌 REST로 한 이유

Kafka Choreography 방식과 REST Orchestration 방식 중 REST를 선택했다.

**Kafka Choreography라면:**
```
transaction-server → Kafka: "saga.started"
bank-server (Consumer) → 이벤트 소비 → Kafka: "bank.transfer.created"
stock-server (Consumer) → 이벤트 소비 → Kafka: "stock.cash.deposited"
transaction-server (Consumer) → 이벤트 소비 → 다음 단계 진행
```

**REST Orchestration (현재):**
```
transaction-server → bank-server: POST /transfers        → 즉시 성공/실패 수신
transaction-server → stock-server: POST /cash/deposit   → 즉시 성공/실패 수신
transaction-server → bank-server: POST /transfers/{id}/approve
```

| 비교 항목 | REST Orchestration | Kafka Choreography |
|-----------|-------------------|--------------------|
| 실패 감지 | 즉시 (HTTP 응답) | Consumer가 응답 이벤트 대기 |
| 보상 시점 | 실패 직후 즉시 | 이벤트 도달 후 |
| 디버깅 | 단일 traceId로 추적 | 여러 서비스 로그 조합 필요 |
| 구현 복잡도 | Orchestrator 1개에 집중 | 각 서버가 이벤트 흐름 알아야 함 |
| 네트워크 | AWS↔OnPrem VPN 직접 호출 | Kafka broker 경유 추가 hop |
| 타임아웃 처리 | OpenFeign 레벨에서 처리 | Correlation ID + 타임아웃 리스너 필요 |

AWS-OnPrem VPN 환경에서 Kafka를 Saga driver로 쓰면 이벤트 전달 지연, 순서 보장, 타임아웃 처리를 모든 서버에서 각각 구현해야 한다. REST Orchestration이 이 규모에 훨씬 적합하다.

## 2-2. Kafka의 실제 용도

이 설계에서 Kafka는 **감사(Audit) 및 알림 전용**이다. Saga 진행 자체에 관여하지 않는다.

```
Saga 실행 완료
→ transaction-server OutboxRelayScheduler → Kafka
→ 감사 로그 / 알림 / 분석 시스템 (현재 Consumer 없음, 향후 연동용)
```

Saga가 이미 완료된 후 그 사실을 외부에 알리는 용도다.

## 2-3. Outbox를 transaction-server에만 적용한 이유

Outbox를 전 서버에 강제하면:
- bank / stock / transaction 모두 OUTBOX_EVENT 테이블 + RelayScheduler + DLQ 관리 필요
- 구현 비용 3배, 운영 복잡도 상승

현재 Kafka Consumer가 없으므로 bank/stock의 이벤트 발행 신뢰도가 낮아도 서비스 동작에 영향이 없다. transaction-server의 Saga 생명주기 이벤트는 감사 목적으로 보장이 필요하므로 Outbox 적용. bank/stock은 Kafka 발행 없이 REST 응답만 반환한다.

## 2-4. bank/stock Kafka 직접 발행을 제거한 이유

현재 Consumer 현황:

| 토픽 | Consumer |
|------|---------|
| `stock.cash.deposit.completed` | 없음 |
| `bank.account.withdraw.completed` | 없음 |
| `saga.completed` | 없음 (향후 감사용) |

아무도 소비하지 않는 이벤트를 발행하면서 consistency model만 섞인다.
- transaction-server: Outbox 경유 → 발행 보장
- stock: 직접 발행 → best effort
- bank: 직접 발행 → optional

`saga.completed` 이벤트 payload에 완료된 단계 정보를 담으면 개별 step 이벤트는 중복이다. transaction-server 이벤트만 사용한다.

## 2-5. Idempotency 패턴을 bank/stock 통일한 이유

stock의 deposit/withdraw 중복 실행 위험:
```
transaction-server → stock: depositCash()
  → stock 처리 완료, 응답 전송
  → 네트워크 timeout (응답 유실)
transaction-server → retry: depositCash()
  → double deposit 발생
```

이를 막기 위해 stock도 bank처럼 `Idempotency-Key` 헤더 기반 deduplication을 사용한다.

**bank 방식:**
- `createTransfer` 호출 시 `Idempotency-Key` 헤더 전달
- bank-server가 해당 키로 중복 처리

**stock 통일 방식:**
- `depositCash` / `withdrawCash` 호출 시 `Idempotency-Key` 헤더 전달
- transaction-server가 sagaId 기반으로 operation별 키 생성
- stock-server가 해당 키로 중복 처리

```
deposit 호출:              Idempotency-Key: "{sagaId}_DEPOSIT"
withdraw 호출:             Idempotency-Key: "{sagaId}_WITHDRAW"
compensation deposit 호출: Idempotency-Key: "{sagaId}_COMPENSATION_DEPOSIT"
compensation withdraw 호출: Idempotency-Key: "{sagaId}_COMPENSATION_WITHDRAW"
```

stock-server는 Saga 개념을 알 필요 없이 표준 Idempotency-Key 패턴만 구현하면 된다. bank와 동일한 인터페이스로 통일된다.

**sagaId를 body에 포함하지 않는 이유:**
- deduplication 역할을 Idempotency-Key 헤더가 담당하므로 sagaId의 존재 이유가 없어짐
- stock-server가 Saga 내부 개념(sagaId)을 알게 되면 레이어 분리가 깨짐

---

# 3. bank 담당자에게 전달할 내용

## 3-1. 기존 API 활용 (변경 없음)

현재 Saga 설계에서 bank-server의 기존 API를 그대로 활용한다. 신규 구현 필요 없음.

| 단계 | 메서드 | 경로 | 동작 |
|------|--------|------|------|
| Transfer 생성 | POST | `/internal/v1/bank/transfers` | 이체 레코드 생성 → `REQUESTED` 반환 (잔액 변동 없음) |
| Transfer 확정 | POST | `/internal/v1/bank/transfers/{id}/approve` | 잔액 검증 + 실제 출금/입금 실행 → `SUCCESS` 반환 |

**Idempotency 현황 (이미 충족):**
- `createTransfer`: `Idempotency-Key` 헤더로 중복 생성 방지 ✅
- `approveTransfer`: 동일 transferId 재호출 시 `TRANSFER_003` 반환 (자연 멱등) ✅

## 3-2. "Reserve"가 아닌 이유 (명칭 수정 제안)

현재 Saga 문서에서 `createTransfer`를 "Reserve"로 표현했으나, 실제 동작은 **잔액을 잠그지 않는다**. 이체 레코드만 INSERT하고 잔액은 `approve` 시점에 처음 차감된다.

실질적 의미: **pending intent recording** (이체 의도 기록)

Saga step명을 `BANK_TRANSFER_RESERVE` → `BANK_TRANSFER_REQUEST_CREATED`로 변경 예정. bank-server 구현에는 영향 없음.

## 3-3. cancel API 추가 — 협의 사항 (강제 아님)

**배경:**

BANK_TO_STOCK Saga에서 STEP 2(stock deposit)가 실패하면:
- `approve`를 호출하지 않으므로 잔액 변동은 없음 (금융 정합성 이상 없음)
- `REQUESTED` 상태의 이체 레코드가 bank DB에 남음
- 현재 설계: Reconciliation 배치가 주기적으로 정리

**cancel API가 있다면:**
```
STEP 2 실패
→ POST /internal/v1/bank/transfers/{id}/cancel
→ REQUESTED → CANCELLED 즉시 처리
→ 배치 의존 없음
```

**추가를 권장하는 이유:**
- 구현 단순: `UPDATE transfer SET status = 'CANCELLED' WHERE id = ? AND status = 'REQUESTED'`
- transfer 테이블에 의미 없는 REQUESTED 레코드 누적 방지
- Saga Case A가 배치 의존 없이 즉시 정리됨
- Reconciliation 배치의 책임 범위 감소

**추가하지 않아도 되는 이유:**
- REQUESTED 상태는 잔액 변동이 없으므로 금융 정합성에 영향 없음
- Reconciliation 배치로 eventual consistency 보장
- bank API 표면 확장 최소화 가능

**결정 권한:** bank 담당자. cancel API 없이 진행할 경우 Reconciliation이 Case A를 처리하는 방식으로 문서 확정.

---

# 4. 서버별 구현 내용

## 4-1. bank-server

**신규 구현 없음.** 기존 `createTransfer` / `approveTransfer` 사용.

Kafka 발행 없음.

## 4-2. stock-server

**신규 구현 대상:**

| 메서드 | 경로 | 동작 |
|--------|------|------|
| POST | `/internal/v1/stock/accounts/{accountId}/cash/deposit` | 예수금 증가 |
| POST | `/internal/v1/stock/accounts/{accountId}/cash/withdraw` | 예수금 차감 |

**공통 헤더:**
```
X-User-Id: {userId}
X-Trace-Id: {traceId}
Idempotency-Key: {sagaId}_{OPERATION_TYPE}   ← transaction-server가 생성해서 전달
```

**Request body:**
```json
{ "amount": 500000 }
```

**Response body:**
```json
{ "accountId": 2001, "cashBalance": 10500000 }
```

**에러:** withdraw 잔액 부족 → `TRANSFER_002`

**Idempotency 구현 (bank와 동일 패턴):**
- `Idempotency-Key` 헤더 수신
- 동일 키로 이미 처리된 요청이면 저장된 응답 반환
- 신규 요청이면 처리 후 결과 저장

Kafka 발행 없음.

**구현 필요 사항:**
- `CashService`: `deposit(accountId, amount, idempotencyKey)` / `withdraw(accountId, amount, idempotencyKey)`
- `CashController`: 위 2개 엔드포인트
- stock 자체 idempotency 저장 로직 (Idempotency-Key 기반)

## 4-3. transaction-server

**전체 신규 구현 대상.**

### 도메인 패키지

| 패키지 | 구현 내용 |
|--------|-----------|
| `domain/saga` | SagaTransaction, SagaStepHistory Entity/Repo, SagaOrchestrator, SagaController |
| `domain/outbox` | OutboxEvent Entity/Repo, OutboxService, OutboxRelayScheduler |
| `domain/deadletter` | DeadLetterEvent Entity/Repo, DeadLetterService |
| `domain/reconciliation` | ReconciliationResult Entity/Repo, ReconciliationService |
| `global` | IdempotencyKey Entity/Repo, TransactionAuditLog Entity/Repo, IdempotencyService, AuditService, KafkaConfig |

### StockCoreClient 추가 메서드

기존 `StockCoreClient`에 2개 추가:

```java
@PostMapping("/internal/v1/stock/accounts/{accountId}/cash/deposit")
ApiResponse<StockCashResponse> depositCash(
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-Trace-Id") String traceId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @PathVariable("accountId") Long accountId,
    @RequestBody StockCashRequest request);

@PostMapping("/internal/v1/stock/accounts/{accountId}/cash/withdraw")
ApiResponse<StockCashResponse> withdrawCash(
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-Trace-Id") String traceId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @PathVariable("accountId") Long accountId,
    @RequestBody StockCashRequest request);
```

`StockCashRequest`: `{ Long amount }`
`StockCashResponse`: `{ Long accountId, BigDecimal cashBalance }`

**Idempotency-Key 생성 규칙 (transaction-server 내부):**

| 호출 | 키 |
|------|----|
| 정상 deposit | `{sagaId}_DEPOSIT` |
| 정상 withdraw | `{sagaId}_WITHDRAW` |
| compensation deposit | `{sagaId}_COMPENSATION_DEPOSIT` |
| compensation withdraw | `{sagaId}_COMPENSATION_WITHDRAW` |

### Saga API 엔드포인트

```
POST /baas/v1/saga/bank-to-stock   Header: Idempotency-Key, X-Firebase-Uid
POST /baas/v1/saga/stock-to-bank   Header: Idempotency-Key, X-Firebase-Uid
GET  /baas/v1/saga/{sagaId}
GET  /baas/v1/saga/{sagaId}/steps
```

**BANK_TO_STOCK 요청:**
```json
{
  "fromBankAccountId": 1001,
  "toStockAccountId": 2001,
  "toBankCode": "039",
  "toAccountNumber": "300-123-456789",
  "amount": 500000
}
```

**STOCK_TO_BANK 요청:**
```json
{
  "fromStockAccountId": 2001,
  "toBankAccountId": 1001,
  "toBankCode": "088",
  "toAccountNumber": "110-123-456789",
  "amount": 500000
}
```

### OutboxRelayScheduler

```java
@Scheduled(fixedDelay = 1000)
void relay() {
    // published_yn = false 이벤트 최대 100건 조회 → Kafka 발행
    // 실패 시 retry_count++
    // retry_count >= 3 → DeadLetterEvent 저장
}
```

### DB 테이블 목록

| 테이블 | 설명 |
|--------|------|
| `SAGA_TRANSACTION` | Saga 트랜잭션 상태 |
| `SAGA_STEP_HISTORY` | 단계별 처리 이력 |
| `OUTBOX_EVENT` | Kafka 발행 보장 |
| `DEAD_LETTER_EVENT` | 3회 실패 이벤트 |
| `IDEMPOTENCY_KEY` | 중복 Saga 요청 방지 |
| `TRANSACTION_AUDIT_LOG` | 거래 감사 로그 |
| `RECONCILIATION_RESULT` | 정합성 검증 결과 |

---

# 5. Saga 흐름

## 5-1. BANK_TO_STOCK 성공 흐름

```
[Client] POST /baas/v1/saga/bank-to-stock  Header: Idempotency-Key
    ↓
[transaction-server]
  1. IdempotencyService.check(idempotencyKey)
     → 이미 존재하면 저장된 응답 반환 (종료)
  2. SagaTransaction INSERT (STARTED)
     + OutboxEvent INSERT (saga.started)
     → 동일 트랜잭션 Commit

  ─── STEP 1: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
  3. BankCoreClient.createTransfer(fromAccountId, toBankCode, toAccountNumber, amount)
     Header: Idempotency-Key = {originalIdempotencyKey}
     → bank: transfer INSERT, 잔액 변동 없음 → REQUESTED 반환
     → SagaStepHistory INSERT (BANK_TRANSFER_REQUEST_CREATED, SUCCESS, transferId 저장)

  ─── STEP 2: STOCK_CASH_DEPOSIT ──────────────────────────────────
  4. StockCoreClient.depositCash(toStockAccountId, amount)
     Header: Idempotency-Key = "{sagaId}_DEPOSIT"
     → stock: cash_balance 증가
     → SagaStepHistory INSERT (STOCK_CASH_DEPOSIT, SUCCESS)

  ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
  5. BankCoreClient.approveTransfer(transferId)
     → bank: 잔액 검증 + 실제 출금 실행 → SUCCESS 반환
     → SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, SUCCESS)
     → SagaTransaction UPDATE (SUCCESS)
     + OutboxEvent INSERT (saga.completed)
     → 동일 트랜잭션 Commit
     → TransactionAuditLog INSERT
     → IdempotencyService.complete(idempotencyKey, response)
```

## 5-2. BANK_TO_STOCK 실패 흐름

### Case A — STEP 2 실패 (stock deposit 오류)

```
StockCoreClient.depositCash() 실패
    ↓
STEP 3(approve) 미호출 → bank 잔액 변동 없음
SagaStepHistory INSERT (STOCK_CASH_DEPOSIT, FAILED)
SagaTransaction UPDATE (FAILED)
+ OutboxEvent INSERT (saga.failed)
→ 동일 트랜잭션 Commit

[cancel API 있는 경우]
BankCoreClient.cancelTransfer(transferId) → REQUESTED → CANCELLED

[cancel API 없는 경우]
REQUESTED 이체 방치 → Reconciliation 배치 주기적 정리
```

### Case B — STEP 3 실패 (bank approve 오류, stock은 이미 예수금 증가)

```
BankCoreClient.approveTransfer() 실패
    ↓
SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, FAILED)
SagaTransaction UPDATE (COMPENSATING)
+ OutboxEvent INSERT (saga.compensation.started)
→ 동일 트랜잭션 Commit

  ─── COMPENSATION: STOCK_CASH_WITHDRAW ───────────────────────────
  StockCoreClient.withdrawCash(toStockAccountId, amount)
  Header: Idempotency-Key = "{sagaId}_COMPENSATION_WITHDRAW"
  → stock: 예수금 차감
  → SagaStepHistory INSERT (STOCK_CASH_WITHDRAW_COMPENSATION, COMPENSATED)
  → SagaTransaction UPDATE (COMPENSATED)
  + OutboxEvent INSERT (saga.compensation.completed)
  → 동일 트랜잭션 Commit
```

## 5-3. STOCK_TO_BANK 성공 흐름

```
[Client] POST /baas/v1/saga/stock-to-bank  Header: Idempotency-Key
    ↓
[transaction-server]
  1. IdempotencyService.check()
  2. SagaTransaction INSERT (STARTED) + OutboxEvent (saga.started)

  ─── STEP 1: STOCK_CASH_WITHDRAW ─────────────────────────────────
  3. StockCoreClient.withdrawCash(fromStockAccountId, amount)
     Header: Idempotency-Key = "{sagaId}_WITHDRAW"
     → stock: 예수금 차감
     → SagaStepHistory INSERT (STOCK_CASH_WITHDRAW, SUCCESS)

  ─── STEP 2: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
  4. BankCoreClient.createTransfer(은행계좌 정보, amount)
     Header: Idempotency-Key = {originalIdempotencyKey}
     → bank: transfer REQUESTED
     → SagaStepHistory INSERT (BANK_TRANSFER_REQUEST_CREATED, SUCCESS)

  ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
  5. BankCoreClient.approveTransfer(transferId)
     → bank: 입금 실행
     → SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, SUCCESS)
     → SagaTransaction UPDATE (SUCCESS) + OutboxEvent (saga.completed)
     → AuditLog + IdempotencyKey 완료
```

## 5-4. STOCK_TO_BANK 실패 흐름

### STEP 1 실패 (stock withdraw 오류 — 잔액 부족 등)

```
StockCoreClient.withdrawCash() 실패
    ↓
SagaStepHistory INSERT (STOCK_CASH_WITHDRAW, FAILED)
SagaTransaction UPDATE (FAILED) + OutboxEvent (saga.failed)
→ 보상 없음 (stock 변동 없음, bank 미호출)
```

### STEP 2/3 실패 (bank 오류, stock 예수금은 이미 차감됨)

```
STEP 2 또는 STEP 3 실패
    ↓
SagaTransaction UPDATE (COMPENSATING) + OutboxEvent (saga.compensation.started)

  ─── COMPENSATION: STOCK_CASH_DEPOSIT ────────────────────────────
  StockCoreClient.depositCash(fromStockAccountId, amount)
  Header: Idempotency-Key = "{sagaId}_COMPENSATION_DEPOSIT"
  → stock: 예수금 복구
  → SagaStepHistory INSERT (STOCK_CASH_DEPOSIT_COMPENSATION, COMPENSATED)
  → SagaTransaction UPDATE (COMPENSATED) + OutboxEvent (saga.compensation.completed)
```

---

# 6. Kafka 이벤트

## 6-1. transaction-server 발행 (Outbox 경유 — 보장됨)

| 토픽 | 발행 시점 |
|------|-----------|
| `saga.started` | Saga 시작 |
| `saga.completed` | 모든 단계 성공 |
| `saga.failed` | 단계 실패, 보상 불필요 |
| `saga.compensation.started` | 보상 트랜잭션 시작 |
| `saga.compensation.completed` | 보상 완료 |

## 6-2. bank-server / stock-server

**Kafka 발행 없음.**

transaction-server의 `saga.completed` payload에 완료 단계 정보를 포함하면 개별 step 이벤트는 중복이다. 현재 Consumer가 없으므로 bank/stock에서 직접 발행하지 않는다.

---

# 7. 내부 API 목록

## 7-1. bank-server (기존 구현 완료)

| 메서드 | 경로 | 구현 상태 |
|--------|------|-----------|
| POST | `/internal/v1/bank/transfers` | ✅ 완료 |
| POST | `/internal/v1/bank/transfers/{id}/approve` | ✅ 완료 |
| POST | `/internal/v1/bank/transfers/{id}/cancel` | ⬜ 협의 중 (bank 담당자 결정) |

## 7-2. stock-server (신규 구현)

| 메서드 | 경로 | 구현 상태 |
|--------|------|-----------|
| POST | `/internal/v1/stock/accounts/{accountId}/cash/deposit` | ❌ 미구현 |
| POST | `/internal/v1/stock/accounts/{accountId}/cash/withdraw` | ❌ 미구현 |

## 7-3. transaction-server Saga API (신규 구현)

| 메서드 | 경로 | 구현 상태 |
|--------|------|-----------|
| POST | `/baas/v1/saga/bank-to-stock` | ❌ 미구현 |
| POST | `/baas/v1/saga/stock-to-bank` | ❌ 미구현 |
| GET | `/baas/v1/saga/{sagaId}` | ❌ 미구현 |
| GET | `/baas/v1/saga/{sagaId}/steps` | ❌ 미구현 |

---

# 8. 구현 순서

```
[1] transaction-server — DB 엔티티 / 레포지토리
    └─ SagaTransaction, SagaStepHistory
    └─ OutboxEvent, DeadLetterEvent
    └─ IdempotencyKey, TransactionAuditLog, ReconciliationResult

[2] stock-server — Cash API
    └─ CashService (deposit / withdraw + idempotency)
    └─ CashController

[3] transaction-server — StockCoreClient 확장
    └─ depositCash() / withdrawCash() 추가 (Idempotency-Key 헤더 포함)

[4] transaction-server — BANK_TO_STOCK 성공 흐름
    └─ SagaOrchestrator.bankToStock()
    └─ SagaController

[5] transaction-server — Idempotency + Audit
    └─ IdempotencyService
    └─ AuditService

[6] transaction-server — Outbox
    └─ OutboxService
    └─ OutboxRelayScheduler
    └─ KafkaConfig

[7] transaction-server — 실패 / Compensation 흐름
    └─ Case A (STEP 2 실패)
    └─ Case B (STEP 3 실패 + Compensation)

[8] transaction-server — STOCK_TO_BANK 흐름
```

---

# 9. SagaStepName 열거형

| 값 | 방향 | 설명 |
|----|------|------|
| `BANK_TRANSFER_REQUEST_CREATED` | 공통 | bank 이체 레코드 생성 (잔액 변동 없음) |
| `STOCK_CASH_DEPOSIT` | BANK_TO_STOCK 정상 | stock 예수금 충전 |
| `BANK_TRANSFER_COMMIT` | 공통 | bank 이체 확정 (실제 출금/입금) |
| `STOCK_CASH_WITHDRAW` | STOCK_TO_BANK 정상 | stock 예수금 차감 |
| `STOCK_CASH_WITHDRAW_COMPENSATION` | BANK_TO_STOCK 보상 | stock 예수금 회수 |
| `STOCK_CASH_DEPOSIT_COMPENSATION` | STOCK_TO_BANK 보상 | stock 예수금 복구 |

---

# 10. SagaTransaction 상태 전이

```
STARTED
  → (STEP 진행) PROCESSING
  → (모든 STEP 성공) SUCCESS

PROCESSING
  → (단계 실패, 보상 불필요) FAILED
  → (단계 실패, 보상 필요) COMPENSATING

COMPENSATING
  → (보상 성공) COMPENSATED
  → (보상 실패) COMPENSATION_FAILED  ← 수동 처리 필요
```

---

# 11. 공통 헤더 규칙

```http
X-User-Id: {xUserId}       ← transaction-server가 내부 호출 시 자동 주입
X-Trace-Id: {uuid}         ← 없으면 UUID 자동 생성
Idempotency-Key: {key}     ← 이체/deposit/withdraw API 필수
```

JWT 인증 없음. transaction-server가 인증 완료 후 내부 호출하는 구조.
