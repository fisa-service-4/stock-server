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
- Saga는 **동기 REST 호출** 기반으로 구동된다 (Kafka가 Saga를 drive하지 않는다)

---

# 2. Kafka 사용 범위

## 2-1. 용도 구분

| 역할 | 담당 |
|------|------|
| Saga 구동 | **REST 동기 호출** (Kafka 아님) |
| Saga 생명주기 이벤트 발행 | transaction-server Outbox → Kafka |
| 예수금 변경 이벤트 발행 | stock-server 직접 발행 (Outbox 없이) |
| 은행 거래 이벤트 발행 | bank-server 직접 발행 (구현 선택) |

## 2-2. Kafka 사용 대상

| 사용 O | 사용 X |
|--------|--------|
| 은행 계좌 → 증권 예수금 충전 | 은행 내부 이체 |
| 증권 예수금 → 은행 계좌 환불 | 증권 내부 주문/체결 |
| Saga 상태 변화 감사 | 단일 DB 내부 거래 |

---

# 3. 핵심 전략

## 3-1. Saga Pattern (Orchestration)

transaction-server가 bank-server / stock-server를 REST로 순서대로 호출한다.
각 단계 실패 시 이전 단계의 보상 트랜잭션(Compensation)을 실행한다.

담당 테이블: `SAGA_TRANSACTION`, `SAGA_STEP_HISTORY`

## 3-2. Transactional Outbox (transaction-server 전용)

Saga 이벤트 발행 보장. bank-server / stock-server에는 Outbox 미적용.

```
Saga 상태 변경
+ OUTBOX_EVENT INSERT
→ 동일 DB 트랜잭션 Commit
→ OutboxRelayScheduler (1초 주기) → Kafka 발행
→ 3회 실패 시 → DEAD_LETTER_EVENT 저장
```

담당 테이블: `OUTBOX_EVENT`, `DEAD_LETTER_EVENT`
담당 서버: **transaction-server만**

## 3-3. Idempotency Key

중복 Saga 요청 방지. 동일 `Idempotency-Key`로 재요청 시 저장된 응답 반환.

담당 테이블: `IDEMPOTENCY_KEY`
담당 서버: transaction-server

## 3-4. Reconciliation

배치 기반 원장 정합성 검증. BANK_TO_STOCK Case A(bank REQUESTED 이체 방치) 후처리 포함.

담당 테이블: `RECONCILIATION_RESULT`
담당 서버: transaction-server

## 3-5. Audit Log

모든 Saga 상태 변화 기록.

담당 테이블: `TRANSACTION_AUDIT_LOG`
담당 서버: transaction-server

---

# 4. 서버별 구현 내용

## 4-1. bank-server

**기존 2-step API를 Saga Reserve / Commit으로 활용한다. 신규 API 없음.**

| 단계 | 메서드 | 경로 | 동작 |
|------|--------|------|------|
| Reserve | POST | `/internal/v1/bank/transfers` | 이체 레코드 생성 → `REQUESTED` 반환 **(잔액 변동 없음)** |
| Commit | POST | `/internal/v1/bank/transfers/{id}/approve` | 실제 출금/입금 실행 → `SUCCESS` 반환 |

**Reserve 요청 body:**
```json
{
  "fromAccountId": 1001,
  "toBankCode": "039",
  "toAccountNumber": "300-123-456789",
  "transferAmount": 500000,
  "requestedBy": "USER"
}
```

**왜 TCC가 아닌 Saga인가:**
- Reserve 단계에서 잔액을 잠그지 않는다 (잔액 변동 없음)
- Cancel API가 없다 → 실패 시 REQUESTED 상태 방치 + Reconciliation 배치 정리
- Commit 실패 시 stock 보상 트랜잭션으로 처리

**Kafka 이벤트 (선택 구현):**

bank-server가 직접 발행 (Outbox 없이). 현재 Saga 구동에 필수 아님.

| 토픽 | 발행 시점 |
|------|-----------|
| `bank.account.withdraw.completed` | approve 성공 |
| `bank.account.withdraw.failed` | approve 실패 |

**구현 필요 사항:** 없음 (기존 API 사용)

---

## 4-2. stock-server

**Saga 전용 예수금 입출금 API 신규 구현 필요 (Phase 5).**

| 단계 | 메서드 | 경로 | 동작 |
|------|--------|------|------|
| Deposit | POST | `/internal/v1/stock/accounts/{accountId}/cash/deposit` | 예수금 증가 (Saga 정상 step) |
| Withdraw | POST | `/internal/v1/stock/accounts/{accountId}/cash/withdraw` | 예수금 차감 (Compensation 또는 역방향 step) |

**Request body (공통):**
```json
{ "amount": 500000, "sagaId": 1001 }
```

**Response body (공통):**
```json
{ "accountId": 2001, "cashBalance": 10500000 }
```

**에러:** withdraw 잔액 부족 → `TRANSFER_002`

**Kafka 이벤트:**

stock-server가 직접 발행 (Outbox 없이). Saga 구동과 무관한 알림용.

| 토픽 | 발행 시점 |
|------|-----------|
| `stock.cash.deposit.completed` | deposit 성공 |
| `stock.cash.deposit.failed` | deposit 실패 |
| `stock.cash.withdraw.completed` | withdraw 성공 |
| `stock.cash.withdraw.failed` | withdraw 실패 |

**구현 필요 사항:**
- `CashService`: `deposit(accountId, amount, sagaId)` / `withdraw(accountId, amount, sagaId)`
- `CashController`: 위 2개 엔드포인트
- Kafka producer 설정 + 이벤트 직접 발행

---

## 4-3. transaction-server

**전체 신규 구현 대상.**

### 4-3-1. 도메인 패키지 구성

| 패키지 | 구현 내용 |
|--------|-----------|
| `domain/saga` | SagaTransaction Entity/Repo, SagaStepHistory Entity/Repo, SagaOrchestrator, SagaController |
| `domain/outbox` | OutboxEvent Entity/Repo, OutboxService, OutboxRelayScheduler |
| `domain/deadletter` | DeadLetterEvent Entity/Repo, DeadLetterService |
| `domain/reconciliation` | ReconciliationResult Entity/Repo, ReconciliationService |
| `global` | IdempotencyKey Entity/Repo, TransactionAuditLog Entity/Repo, IdempotencyService, AuditService, KafkaConfig |

### 4-3-2. StockCoreClient 추가 메서드

기존 `StockCoreClient`에 2개 추가:

```java
@PostMapping("/internal/v1/stock/accounts/{accountId}/cash/deposit")
ApiResponse<StockCashResponse> depositCash(
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-Trace-Id") String traceId,
    @PathVariable("accountId") Long accountId,
    @RequestBody StockCashRequest request);

@PostMapping("/internal/v1/stock/accounts/{accountId}/cash/withdraw")
ApiResponse<StockCashResponse> withdrawCash(
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-Trace-Id") String traceId,
    @PathVariable("accountId") Long accountId,
    @RequestBody StockCashRequest request);
```

**StockCashRequest:** `{ Long amount, Long sagaId }`
**StockCashResponse:** `{ Long accountId, BigDecimal cashBalance }`

### 4-3-3. Saga API 엔드포인트

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

### 4-3-4. OutboxRelayScheduler

```java
@Scheduled(fixedDelay = 1000)  // 1초 주기
void relay() {
    // published_yn = false 이벤트 최대 100건 조회
    // Kafka 발행 성공 → published_yn = true
    // 실패 시 retry_count++
    // retry_count >= 3 → DeadLetterEvent 저장
}
```

### 4-3-5. DB 테이블 목록 (transaction-server DB = Oracle XE 21c)

| 테이블 | 설명 |
|--------|------|
| `SAGA_TRANSACTION` | Saga 트랜잭션 상태 |
| `SAGA_STEP_HISTORY` | 단계별 처리 이력 |
| `OUTBOX_EVENT` | Kafka 발행 보장 |
| `DEAD_LETTER_EVENT` | 3회 실패 이벤트 |
| `IDEMPOTENCY_KEY` | 중복 요청 방지 |
| `TRANSACTION_AUDIT_LOG` | 거래 감사 로그 |
| `RECONCILIATION_RESULT` | 정합성 검증 결과 |

---

# 5. Saga 흐름

## 5-1. BANK_TO_STOCK 성공 흐름 (은행 → 증권 예수금 충전)

```
[Client] POST /baas/v1/saga/bank-to-stock  Header: Idempotency-Key
    ↓
[transaction-server]
  1. IdempotencyService.check(idempotencyKey)
     → 이미 존재하면 저장된 응답 반환 (종료)
  2. SagaTransaction INSERT (status=STARTED)
     + OutboxEvent INSERT (saga.started)
     → 동일 트랜잭션 Commit

  ─── STEP 1: BANK_TRANSFER_RESERVE ───────────────────────────────
  3. BankCoreClient.createTransfer(fromAccountId, toBankCode, toAccountNumber, amount)
     → bank-server: transfer 레코드 INSERT, 잔액 변동 없음 → REQUESTED 반환
     → SagaStepHistory INSERT (BANK_TRANSFER_RESERVE, SUCCESS, transferId 저장)

  ─── STEP 2: STOCK_CASH_DEPOSIT ──────────────────────────────────
  4. StockCoreClient.depositCash(toStockAccountId, amount, sagaId)
     → stock-server: cash_balance 증가 → 잔액 반환
     → SagaStepHistory INSERT (STOCK_CASH_DEPOSIT, SUCCESS)

  ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
  5. BankCoreClient.approveTransfer(transferId)
     → bank-server: 잔액 검증 + 실제 출금 실행 → SUCCESS 반환
     → SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, SUCCESS)
     → SagaTransaction UPDATE (status=SUCCESS)
     + OutboxEvent INSERT (saga.completed)
     → 동일 트랜잭션 Commit
     → TransactionAuditLog INSERT
     → IdempotencyService.complete(idempotencyKey, response)

  6. 응답 반환
```

## 5-2. BANK_TO_STOCK 실패 흐름

### Case A — STEP 2 실패 (stock deposit 오류)

```
stock-server 5xx 또는 잔액 오류 응답
    ↓
approve 미호출 → bank 잔액 변동 없음
SagaStepHistory INSERT (STOCK_CASH_DEPOSIT, FAILED)
SagaTransaction UPDATE (status=FAILED)
+ OutboxEvent INSERT (saga.failed)
→ 동일 트랜잭션 Commit

※ bank REQUESTED 이체 레코드는 방치됨
  → Reconciliation 배치가 주기적으로 REQUESTED 방치 건 감지 + 처리
```

### Case B — STEP 3 실패 (bank approve 오류)

```
bank-server approve 실패 (TRANSFER_002 잔액 부족 등)
    ↓
SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, FAILED)
SagaTransaction UPDATE (status=COMPENSATING)
+ OutboxEvent INSERT (saga.compensation.started)
→ 동일 트랜잭션 Commit

  ─── COMPENSATION: STOCK_CASH_WITHDRAW ───────────────────────────
  StockCoreClient.withdrawCash(toStockAccountId, amount, sagaId)
  → stock-server: 이미 증가된 예수금 차감
  → SagaStepHistory INSERT (STOCK_CASH_WITHDRAW_COMPENSATION, COMPENSATED)
  → SagaTransaction UPDATE (status=COMPENSATED)
  + OutboxEvent INSERT (saga.compensation.completed)
  → 동일 트랜잭션 Commit

※ Compensation도 실패하면 COMPENSATION_FAILED 상태, 수동 처리 필요
```

---

## 5-3. STOCK_TO_BANK 성공 흐름 (증권 예수금 → 은행 계좌 환불)

```
[Client] POST /baas/v1/saga/stock-to-bank  Header: Idempotency-Key
    ↓
[transaction-server]
  1. IdempotencyService.check()
  2. SagaTransaction INSERT (STARTED) + OutboxEvent (saga.started)

  ─── STEP 1: STOCK_CASH_WITHDRAW ─────────────────────────────────
  3. StockCoreClient.withdrawCash(fromStockAccountId, amount, sagaId)
     → stock-server: 예수금 차감
     → SagaStepHistory INSERT (STOCK_CASH_WITHDRAW, SUCCESS)

  ─── STEP 2: BANK_TRANSFER_RESERVE ───────────────────────────────
  4. BankCoreClient.createTransfer(은행계좌, 입금계좌정보, amount)
     → bank-server: transfer REQUESTED
     → SagaStepHistory INSERT (BANK_TRANSFER_RESERVE, SUCCESS)

  ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
  5. BankCoreClient.approveTransfer(transferId)
     → bank-server: 입금 실행
     → SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, SUCCESS)
     → SagaTransaction UPDATE (SUCCESS) + OutboxEvent (saga.completed)
     → AuditLog + IdempotencyKey 완료
```

## 5-4. STOCK_TO_BANK 실패 흐름

### STEP 1 실패 (stock withdraw 오류 — 잔액 부족 등)

```
StockCoreClient.withdrawCash → TRANSFER_002
    ↓
SagaStepHistory INSERT (STOCK_CASH_WITHDRAW, FAILED)
SagaTransaction UPDATE (FAILED) + OutboxEvent (saga.failed)
→ 보상 없음 (stock 변동 없음, bank 미호출)
```

### STEP 2/3 실패 (bank 오류, stock은 이미 차감됨)

```
STEP 2 또는 STEP 3 실패
    ↓
SagaTransaction UPDATE (COMPENSATING) + OutboxEvent (saga.compensation.started)

  ─── COMPENSATION: STOCK_CASH_DEPOSIT ────────────────────────────
  StockCoreClient.depositCash(fromStockAccountId, amount, sagaId)
  → stock-server: 차감된 예수금 복구
  → SagaStepHistory INSERT (STOCK_CASH_DEPOSIT_COMPENSATION, COMPENSATED)
  → SagaTransaction UPDATE (COMPENSATED) + OutboxEvent (saga.compensation.completed)
```

---

# 6. Kafka 토픽

## 6-1. transaction-server 발행 (Outbox 경유 → 보장됨)

| 토픽 | 발행 시점 |
|------|-----------|
| `saga.started` | Saga 시작 |
| `saga.completed` | 모든 단계 성공 |
| `saga.failed` | 단계 실패, 보상 불필요 |
| `saga.compensation.started` | 보상 트랜잭션 시작 |
| `saga.compensation.completed` | 보상 완료 |

## 6-2. stock-server 발행 (직접 발행 → Outbox 미적용)

| 토픽 | 발행 시점 |
|------|-----------|
| `stock.cash.deposit.completed` | 예수금 충전 성공 |
| `stock.cash.deposit.failed` | 예수금 충전 실패 |
| `stock.cash.withdraw.completed` | 예수금 차감 성공 |
| `stock.cash.withdraw.failed` | 예수금 차감 실패 |

## 6-3. bank-server 발행 (직접 발행 → 선택 구현)

| 토픽 | 발행 시점 |
|------|-----------|
| `bank.account.withdraw.completed` | approve 출금 성공 |
| `bank.account.withdraw.failed` | approve 출금 실패 |
| `bank.account.deposit.completed` | approve 입금 성공 |
| `bank.account.deposit.failed` | approve 입금 실패 |

---

# 7. 내부 API 목록

## 7-1. bank-server (모두 기존 구현 완료)

| 메서드 | 경로 | 용도 |
|--------|------|------|
| POST | `/internal/v1/bank/transfers` | 이체 Reserve (잔액 변동 없음) |
| POST | `/internal/v1/bank/transfers/{id}/approve` | 이체 Commit (실제 출금/입금) |
| GET | `/internal/v1/bank/transfers/{id}` | 이체 상태 조회 |

## 7-2. stock-server (신규 구현 필요)

| 메서드 | 경로 | 용도 | 구현 상태 |
|--------|------|------|-----------|
| POST | `/internal/v1/stock/accounts/{accountId}/cash/deposit` | 예수금 충전 | **미구현** |
| POST | `/internal/v1/stock/accounts/{accountId}/cash/withdraw` | 예수금 차감 | **미구현** |

## 7-3. transaction-server Saga API (신규 구현 필요)

| 메서드 | 경로 | 용도 | 구현 상태 |
|--------|------|------|-----------|
| POST | `/baas/v1/saga/bank-to-stock` | 은행→증권 충전 Saga | **미구현** |
| POST | `/baas/v1/saga/stock-to-bank` | 증권→은행 환불 Saga | **미구현** |
| GET | `/baas/v1/saga/{sagaId}` | Saga 상태 조회 | **미구현** |
| GET | `/baas/v1/saga/{sagaId}/steps` | Saga 단계 이력 조회 | **미구현** |

---

# 8. 구현 순서

```
[1] stock-server
    └─ CashService (deposit / withdraw)
    └─ CashController
    └─ Kafka producer 직접 발행 (Outbox 없음)

[2] transaction-server — DB 엔티티
    └─ SagaTransaction, SagaStepHistory
    └─ OutboxEvent, DeadLetterEvent
    └─ IdempotencyKey, TransactionAuditLog, ReconciliationResult

[3] transaction-server — StockCoreClient 확장
    └─ depositCash() / withdrawCash() 추가

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

[7] transaction-server — 실패/Compensation 흐름
    └─ Case A (STEP 2 실패)
    └─ Case B (STEP 3 실패 + Compensation)

[8] transaction-server — STOCK_TO_BANK 흐름
```

---

# 9. 공통 헤더 규칙

```http
X-User-Id: {xUserId}      ← transaction-server가 내부 호출 시 자동 주입
X-Trace-Id: {uuid}        ← 없으면 UUID 자동 생성
```

JWT 인증 없음. transaction-server가 인증 완료 후 내부 호출하는 구조.

---

# 10. SagaStepName 열거형

| 값 | 방향 | 설명 |
|----|------|------|
| `BANK_TRANSFER_RESERVE` | BANK_TO_STOCK / STOCK_TO_BANK | bank 이체 예약 |
| `STOCK_CASH_DEPOSIT` | BANK_TO_STOCK | stock 예수금 충전 |
| `BANK_TRANSFER_COMMIT` | BANK_TO_STOCK / STOCK_TO_BANK | bank 이체 확정 |
| `STOCK_CASH_WITHDRAW` | STOCK_TO_BANK | stock 예수금 차감 |
| `STOCK_CASH_WITHDRAW_COMPENSATION` | BANK_TO_STOCK Compensation | stock 예수금 회수 |
| `STOCK_CASH_DEPOSIT_COMPENSATION` | STOCK_TO_BANK Compensation | stock 예수금 복구 |

---

# 11. SagaTransaction 상태 전이

```
STARTED
  → (STEP 1 성공) PROCESSING
  → (STEP 2 성공) PROCESSING
  → (STEP 3 성공) SUCCESS

PROCESSING
  → (단계 실패, 보상 불필요) FAILED
  → (단계 실패, 보상 필요) COMPENSATING

COMPENSATING
  → (보상 성공) COMPENSATED
  → (보상 실패) COMPENSATION_FAILED  ← 수동 처리 필요
```
