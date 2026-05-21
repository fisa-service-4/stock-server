PHASE 1 — 기본 도메인 구축

목표:
- stock-server 기본 뼈대 구축
- 실제 한국투자 REST API 구조 반영
- 외부 DTO / 내부 Domain 분리

전략:
현재는 REST 응답 기준으로 구현
→ 이후 WebSocket 확장 가능 구조 유지

────────────────────────────────────

1-1. 프로젝트 구조 정리

현재 구조 기반으로 domain 세분화

예:

domain/
├─ stock/
│   ├─ controller/
│   ├─ service/
│   ├─ repository/
│   ├─ entity/
│   ├─ dto/
│   ├─ mapper/
│   └─ exception/

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

────────────────────────────────────

1-3. 실제 REST API 분석

분석 대상:
- 현재가 REST API
- 계좌 REST API
- 주문 REST API

예시 응답:

{
"rt_cd": "0",
"msg_cd": "MCA00000",
"msg1": "정상처리 되었습니다.",
"output": {
"stck_prpr": "80500",
"prdy_vrss": "1200"
}
}

────────────────────────────────────

1-4. 외부 DTO 생성

예:

- KisCurrentPriceResponse
- KisBalanceResponse
- KisOrderResponse

주의:
실제 한국투자 REST 응답 구조 반영

────────────────────────────────────

1-5. 내부 Domain 생성

예:

- StockPrice
- Order
- Execution
- Holding

주의:
내부 Domain은 우리 기준으로 설계

────────────────────────────────────

1-6. Mapper 생성

예:

KisMapper

역할:

외부 DTO
→ 내부 Domain 변환

────────────────────────────────────

1-7. DummyKisClient 구현

목표:
실제 API 대신 더미 응답 제공

중요:
실제 REST 응답 구조 mimic

BAD ❌

{
"price": 1000
}

GOOD ⭕

{
"rt_cd": "0",
"output": {
"stck_prpr": "80500"
}
}

────────────────────────────────────

1-8. STOCK_MASTER 구현

기능:
- 종목 검색
- 종목 상세 조회

API:
GET /stocks/search

초기 데이터:
- 삼성전자
- SK하이닉스
- NAVER
- 카카오

────────────────────────────────────

1-9. SECURITIES_ACCOUNT 구현

기능:
- 계좌 조회
- 예수금 조회
- 출금가능금액 조회

API:
GET /accounts
GET /accounts/{id}
GET /accounts/{id}/balance

────────────────────────────────────

1-10. 공통 응답/예외 처리 연결

사용:
- global/response/
- global/exception/

────────────────────────────────────

PHASE 1 완료 기준:

✅ 외부 DTO 분리 완료
✅ 내부 Domain 분리 완료
✅ Dummy REST 응답 구조 구현 완료
✅ 종목 조회 가능
✅ 계좌 조회 가능
✅ 실제 REST API 반영 구조 완성