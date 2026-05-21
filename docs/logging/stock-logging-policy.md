# 증권(Core) 로깅 정책

stock-server 로그 작성 기준 문서.

---

## 목표

- 주문/체결/시세/계좌 흐름 추적 가능
- 장애 원인 분석 가능
- traceId 기반 서버 간 요청 추적 가능
- 사용자 금융 행위 감사(Audit) 가능

---

## 로그 레벨 정책

### INFO — 정상 비즈니스 흐름

| 대상 | 예시 |
|------|------|
| 주문 요청 / 접수 / 체결 완료 / 취소 완료 | `주문 체결 완료 orderId=1023` |
| 예수금 입금 / 출금 완료 | `예수금 입금 완료 amount=100000` |
| 포트폴리오 / 잔고 조회 | `잔고 조회 userId=15` |
| KIS API 호출 성공 / 시세 조회 성공 | `시세 조회 성공 stockCode=005930` |
| 스케줄러 실행 시작 / 완료 | `시세 스케줄러 실행 완료` |

### WARN — 예상 가능한 실패

| 대상 | 예시 |
|------|------|
| 잔액 부족 | `잔액 부족 available=50000 required=735000` |
| 보유 수량 부족 | `보유 수량 부족 holding=3 required=10` |
| KIS API 재시도 발생 / 응답 지연 | `KIS API 재시도 attempt=2` |
| PIN 불일치 / 권한 없음 | `PIN 불일치 userId=15` |

### ERROR — 운영 개입 필요

| 대상 | 예시 |
|------|------|
| DB 오류 | `DB 저장 실패 orderId=1023` |
| 체결 처리 실패 / 주문 저장 실패 | `체결 처리 실패 orderId=1023` |
| 예상치 못한 Exception | GlobalExceptionHandler catch-all 진입 시 |
| 외부 API 장애 | `KIS API 장애 stockCode=005930` |
| Kafka 발행 실패 | `Kafka 발행 실패 topic=stock.order.completed` |

### DEBUG — 개발 환경 전용

- **prod: OFF**
- local / dev: 허용

허용 대상:
- 외부 API raw response (개인정보 제외)
- SQL 추적
- 개발용 데이터 흐름

금지:
- 개인정보
- 주문 원문 전체

---

## 로그 찍는 위치

| 위치 | 정책 |
|------|------|
| Controller | 요청 진입 / 응답 반환 |
| Service | 핵심 비즈니스 로직 (주문/체결/잔고 변경) |
| GlobalExceptionHandler | ERROR 중앙 처리 |
| TraceLoggingFilter | traceId MDC 저장, 요청 시작/종료 |
| Repository | **금지** |

---

## 로그 포맷

```text
[LEVEL] [traceId=xxx] [userId=15] message key=value
```

예시:

```text
[INFO]  [traceId=a1b2c3] [userId=15] 주문 요청 stockCode=005930 orderType=BUY quantity=10 price=82000
[INFO]  [traceId=a1b2c3] [userId=15] 주문 체결 완료 orderId=1023
[WARN]  [traceId=a1b2c3] [userId=15] 잔액 부족 available=50000 required=735000
[ERROR] [traceId=a1b2c3] 체결 처리 실패 orderId=1023
```

---

## traceId 정책

### 생성 책임

```text
traceId 생성 책임 = transaction-server (온프레미스 진입점)

흐름:
  Client → service-backend → transaction-server (X-Trace-Id 생성)
    → stock-server  (X-Trace-Id 헤더 수신 → MDC 저장 → meta.traceId echo)
    → bank-server   (동일)
```

### stock-server TraceLoggingFilter 정책

| 상황 | MDC traceId | 응답 meta.traceId |
|------|-------------|------------------|
| X-Trace-Id 헤더 있음 | 헤더 값 사용 | 헤더 값 echo |
| X-Trace-Id 헤더 없음 | UUID 신규 생성 (로그 추적용) | **null** |

> MDC traceId와 응답 meta.traceId가 다를 수 있음.
> 헤더 없는 경우는 개발/테스트 직접 호출 시에만 발생하며,
> 운영 환경에서는 transaction-server가 항상 헤더를 주입함.

### 모든 로그에 필수 포함

```text
traceId=...
```

---

## userId 정책

### 허용

```text
userId=15   ← Long 타입 내부 ID만 허용
```

### 금지

```text
이메일
전화번호
계좌번호 전체
PIN
Access Token / JWT
주민번호
```

---

## 계좌번호 마스킹 정책

| 구분 | 예시 |
|------|------|
| 허용 | `123-****-5678` |
| 금지 | `123-45-67890` |

로그에 계좌번호 전체 출력 금지.

---

## 주문 로그 필수 항목

주문 요청 시 반드시 포함:

| 항목 | 포함 여부 |
|------|----------|
| traceId | O |
| userId | O |
| stockCode | O |
| orderType | O |
| quantity | O |
| price | O |
| orderId (체결 이후) | O |

---

## 절대 금지 로그

```text
주문 PIN
JWT 전체 / Access Token
계좌번호 전체
개인정보 Raw (이메일, 전화번호, 주민번호)
외부 API 응답 전체 (KIS raw response 등)
```

---

## Spring 구현 가이드

### 사용 기술

```text
@Slf4j          — 로거 선언
MDC             — traceId / userId 컨텍스트 전파
OncePerRequestFilter — TraceLoggingFilter 구현
```

### TraceLoggingFilter (필수 구현)

역할:
- X-Trace-Id 헤더 추출 (없으면 UUID 생성) → MDC 저장
- 요청 시작 / 종료 INFO 로그

```java
MDC.put("traceId", traceId);
// 요청 처리
MDC.clear(); // finally에서 반드시 정리
```

### GlobalExceptionHandler

역할:
- 모든 예외 ERROR 로그 중앙 처리
- MDC에서 traceId 읽어 포함

---

## 최소 도입 우선순위

### 1단계 (필수)

- TraceLoggingFilter — MDC traceId 주입
- GlobalExceptionHandler — ERROR 로그
- 요청 시작 / 종료 INFO 로그

### 2단계

- 주문 / 체결 INFO 로그
- 잔액 부족 / 보유 수량 부족 WARN 로그
- 외부 API WARN 로그

### 3단계

- 구조화 로그 (JSON)
- ELK / Grafana 연동
