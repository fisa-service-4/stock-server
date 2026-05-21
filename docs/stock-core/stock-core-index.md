# Stock Core Development Plan

stock-server 개발 로드맵 문서

---

# 목표

프리랜서 특화 핀테크 플랫폼의 증권 도메인 서버 구축

주요 역할:
- 증권 계좌 관리
- 주문/체결 처리
- 시세 조회
- 보유 종목 관리
- 수익률 계산
- Kafka 이벤트 발행
- Saga 연동 지원

---

# Architecture Goal

현재:
- REST 기반 구조 우선 구현
- Dummy 기반 개발

이후:
- WebSocket 실시간 시세 확장
- Kafka/Event 기반 구조 확장
- Outbox 기반 안정성 확보

---

# Development Phases

| Phase | 내용 |
|---|---|
| Phase 1 | 기본 도메인 구축 |
| Phase 2 | 시세 시스템 구축 |
| Phase 3 | 주문/체결 엔진 구축 |
| Phase 4 | 보유종목/수익률 구축 |
| Phase 5 | 예수금 + Kafka 연동 |
| Phase 6 | Outbox/Event 안정화 |

---

## Development Phases

- @plan/phase1.md
- @plan/phase2.md
- @plan/phase3.md
- @plan/phase4.md
- @plan/phase5.md
- @plan/phase6.md

---

# Current Strategy

초기 구현:
- Dummy 기반 개발
- REST 응답 구조 반영
- 외부 DTO / 내부 Domain 분리

추후 확장:
- 실시간 WebSocket
- Kafka Event
- Saga
- Outbox Pattern

---

# Core Domain

주요 도메인:

- STOCK_MASTER
- SECURITIES_ACCOUNT
- STOCK_ORDER
- STOCK_EXECUTION
- STOCK_HOLDING
- STOCK_PRICE_HISTORY
- OUTBOX_EVENT

---

# API Direction

외부 API:
- 한국투자 OpenAPI 구조 참고

내부 API:
- transaction-server 연동 고려
- Saga orchestration 대응

---

# Event Direction

Kafka Topic 예시:

- stock.cash.deposit.completed
- stock.cash.withdraw.completed
- stock.order.completed
- stock.order.failed

---

# Final Goal

실제 증권 서버 구조를 단순화하여:

- 금융 도메인 흐름 이해
- MSA 구조 학습
- Event Driven Architecture 경험
- 금융 트랜잭션 처리 경험

을 목표로 한다.