PHASE 1 — 기본 도메인 구축

목표:
- stock-server 기본 뼈대 구축
- 실제 한국투자 REST API 구조 반영 (현재가 API 한정)
- 외부 DTO / 내부 Domain 분리

전략:
REST 응답 기준으로 구현
KIS DTO는 현재가 응답 구조(KisCurrentPriceResponse)만 우선 구현

────────────────────────────────────

사전 조건 (Phase 1 시작 전 필수)

- 패키지명: com.stock 유지 (변경 없음)
- DB 드라이버: build.gradle에서 postgresql → ojdbc11 교체
- application.yaml: Oracle datasource / JPA dialect / port 8082 구성
- SecurityConfig: CSRF 비활성화, 모든 요청 허용 (내부 서버, JWT 처리 없음)

────────────────────────────────────

1-1. 프로젝트 구조 정리

domain 세분화 (portfolio 포함)

domain/
├─ stock/
│   ├─ controller/
│   ├─ service/
│   ├─ repository/
│   ├─ entity/
│   ├─ dto/
│   │   ├─ request/
│   │   └─ response/
│   └─ exception/
├─ account/
│   └─ (동일 구조)
├─ order/
│   └─ (동일 구조)
├─ holding/
│   └─ (동일 구조)
├─ execution/
│   └─ (동일 구조)
└─ portfolio/       ← API 명세 / DB 명세에 존재하므로 초기 구조에 포함
    └─ (동일 구조)

────────────────────────────────────

1-2. external 패키지 생성

목표:
실제 증권 API 구조 분리

구조:

external/
└─ kis/
    ├─ dto/
    ├─ client/
    ├─ mapper/
    └─ dummy/

주의:
websocket/ 디렉터리는 이 단계에서 생성하지 않음
→ Phase 4 완료 후 WebSocket 확장 시 추가

────────────────────────────────────

1-3. 현재가 REST API 구조 분석

분석 대상:
- 현재가 REST API 만 분석 (계좌/주문 API는 해당 Phase에서)

KIS 응답 envelope 구조:

{
  "rt_cd": "0",
  "msg_cd": "MCA00000",
  "msg1": "정상처리 되었습니다.",
  "output": {
    "stck_prpr": "80500",
    "prdy_vrss": "1200",
    "prdy_ctrt": "-1.47"
  }
}

────────────────────────────────────

1-4. 외부 DTO 생성

이 Phase에서 생성:

- KisCurrentPriceResponse   ← 현재가 응답 (rt_cd / msg_cd / output 포함)

이후 Phase에서 생성:

- KisBalanceResponse        ← Phase 3 (계좌/예수금)
- KisOrderResponse          ← Phase 3 (주문)

주의:
실제 KIS REST 응답 envelope 구조 그대로 반영

────────────────────────────────────

1-5. 내부 Domain 생성 (Entity)

이 Phase에서 생성:

- StockMaster               ← STOCK_MASTER 테이블
- SecuritiesAccount         ← SECURITIES_ACCOUNT 테이블

Oracle 타입 매핑 주의:
- ENUM → VARCHAR2 + @Enumerated(EnumType.STRING)
- DECIMAL(18,2) → NUMBER(18,2) → BigDecimal

────────────────────────────────────

1-6. KisMapper 생성

역할:
KisCurrentPriceResponse → 내부 도메인 변환

────────────────────────────────────

1-7. DummyKisClient 구현

목표:
실제 API 대신 더미 응답 제공

구조:
KisClient (인터페이스) + DummyKisClient (구현체)
→ 이후 실제 클라이언트로 교체 가능

중요:
실제 KIS REST 응답 구조 mimic

BAD ❌
{ "price": 1000 }

GOOD ⭕
{
  "rt_cd": "0",
  "output": { "stck_prpr": "80500" }
}

────────────────────────────────────

1-8. STOCK_MASTER 구현

기능:
- 종목 검색 (keyword 기반)

API:
GET /internal/v1/stocks/search?keyword={keyword}

초기 데이터 (DataInitializer로 삽입):
- 005930 삼성전자 KOSPI
- 000660 SK하이닉스 KOSPI
- 035420 NAVER KOSPI
- 035720 카카오 KOSDAQ

────────────────────────────────────

1-9. SECURITIES_ACCOUNT 구현

기능:
- 주문 가능 계좌 조회
- 예수금 조회

API:
GET /internal/v1/stocks/accounts
GET /internal/v1/stocks/cash-balance

초기 데이터 (DataInitializer로 삽입):
- 테스트용 계좌 1개 (초기 예수금 10,000,000원)

────────────────────────────────────

1-10. 공통 응답/예외 처리 구성

global/response/
- ApiResponse<T>     ← { success, data, error, meta(traceId) }

global/exception/
- ErrorCode          ← 에러 코드 enum (error-code.md 기준)
- GlobalException    ← RuntimeException 기반 공통 예외
- GlobalExceptionHandler ← @RestControllerAdvice

global/config/
- SecurityConfig     ← CSRF off, 전체 permitAll
- DataInitializer    ← @PostConstruct 기반 시드 데이터 삽입

────────────────────────────────────

PHASE 1 완료 기준:

✅ 패키지명 / DB 드라이버 / application.yaml 수정 완료
✅ SecurityConfig 적용 (전체 허용)
✅ ApiResponse / ErrorCode / GlobalExceptionHandler 구성
✅ KisClient 인터페이스 + DummyKisClient 구현
✅ KisCurrentPriceResponse (KIS envelope 구조 반영)
✅ STOCK_MASTER 엔티티 + GET /internal/v1/stocks/search 동작
✅ SECURITIES_ACCOUNT 엔티티 + GET /internal/v1/stocks/accounts 동작
✅ GET /internal/v1/stocks/cash-balance 동작
✅ DataInitializer로 시드 데이터 삽입 확인
✅ portfolio/ 도메인 디렉터리 구조 생성
