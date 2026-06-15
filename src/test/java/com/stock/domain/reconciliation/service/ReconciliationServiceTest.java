package com.stock.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.execution.repository.StockExecutionRepository;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.entity.enums.OrderedBy;
import com.stock.domain.order.repository.StockOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

  @Mock private StockOrderRepository stockOrderRepository;
  @Mock private StockExecutionRepository stockExecutionRepository;
  @Mock private StockHoldingRepository stockHoldingRepository;
  @Mock private SecuritiesAccountRepository securitiesAccountRepository;

  @InjectMocks private ReconciliationService reconciliationService;

  // ── 헬퍼 ──────────────────────────────────────────────────────────────────

  private StockOrder buildOrder(Long id) {
    StockOrder order =
        StockOrder.builder()
            .securitiesAccountId(1L)
            .stockCode("005930")
            .orderType(OrderType.BUY)
            .orderMethod(OrderMethod.MARKET)
            .orderQuantity(10)
            .orderedBy(OrderedBy.USER)
            .orderedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(order, "stockOrderId", id);
    return order;
  }

  private StockHolding buildHolding(Long accountId, String stockCode, int quantity) {
    return StockHolding.builder()
        .securitiesAccountId(accountId)
        .stockCode(stockCode)
        .holdingQuantity(quantity)
        .averagePurchasePrice(new BigDecimal("70000"))
        .totalPurchaseAmount(new BigDecimal("700000"))
        .build();
  }

  /** Object[] 행: [accountId(Long), stockCode(String), netQty(Long)] */
  private Object[] executionRow(long accountId, String stockCode, long netQty) {
    return new Object[] {accountId, stockCode, netQty};
  }

  /** List<Object[]> 빌더 — Java 타입 추론 우회 */
  private List<Object[]> rowList(Object[]... items) {
    List<Object[]> list = new ArrayList<>();
    for (Object[] item : items) list.add(item);
    return list;
  }

  private SecuritiesAccount buildAccount(Long userId, BigDecimal cashBalance) {
    SecuritiesAccount acc =
        SecuritiesAccount.builder()
            .userId(userId)
            .brokerCode("243")
            .accountNumber("300-000-" + userId)
            .accountName("테스트계좌")
            .cashBalance(cashBalance)
            .withdrawableBalance(cashBalance)
            .accountStatus(AccountStatus.ACTIVE)
            .openedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(acc, "securitiesAccountId", userId);
    return acc;
  }

  // ── checkOrderExecutionConsistency ────────────────────────────────────────

  @Nested
  @DisplayName("checkOrderExecutionConsistency — 주문-체결 수량 정합성")
  class CheckOrderExecutionConsistency {

    @Test
    @DisplayName("불일치 없으면 0 반환")
    void noMismatch_returnsZero() {
      when(stockOrderRepository.findOrdersWithExecutionMismatch(any()))
          .thenReturn(Collections.emptyList());

      assertThat(reconciliationService.checkOrderExecutionConsistency()).isZero();
    }

    @Test
    @DisplayName("불일치 2건이면 2 반환")
    void twoMismatches_returnsTwo() {
      when(stockOrderRepository.findOrdersWithExecutionMismatch(any()))
          .thenReturn(List.of(buildOrder(1L), buildOrder(2L)));

      assertThat(reconciliationService.checkOrderExecutionConsistency()).isEqualTo(2);
    }
  }

  // ── checkHoldingConsistency ───────────────────────────────────────────────

  @Nested
  @DisplayName("checkHoldingConsistency — 체결-보유 수량 정합성")
  class CheckHoldingConsistency {

    @Test
    @DisplayName("체결과 보유 수량이 일치하면 0 반환")
    void allMatch_returnsZero() {
      when(stockExecutionRepository.findNetQuantityPerAccountAndStock())
          .thenReturn(rowList(executionRow(1L, "005930", 10L)));
      when(stockHoldingRepository.findAll()).thenReturn(List.of(buildHolding(1L, "005930", 10)));

      assertThat(reconciliationService.checkHoldingConsistency()).isZero();
    }

    @Test
    @DisplayName("체결 순수량과 보유수량이 다르면 불일치로 카운트된다")
    void executionAndHoldingDiffer_countsMismatch() {
      // 체결 순수량=10, 실제 보유=8 → 불일치
      when(stockExecutionRepository.findNetQuantityPerAccountAndStock())
          .thenReturn(rowList(executionRow(1L, "005930", 10L)));
      when(stockHoldingRepository.findAll()).thenReturn(List.of(buildHolding(1L, "005930", 8)));

      assertThat(reconciliationService.checkHoldingConsistency()).isEqualTo(1);
    }

    @Test
    @DisplayName("체결 기록은 없지만 보유수량이 양수인 경우 불일치로 카운트된다")
    void holdingWithNoExecution_positiveSQuantity_countsMismatch() {
      when(stockExecutionRepository.findNetQuantityPerAccountAndStock())
          .thenReturn(Collections.emptyList());
      when(stockHoldingRepository.findAll()).thenReturn(List.of(buildHolding(1L, "005930", 5)));

      assertThat(reconciliationService.checkHoldingConsistency()).isEqualTo(1);
    }

    @Test
    @DisplayName("체결 기록 없고 보유수량이 0이면 정상으로 처리한다")
    void holdingWithNoExecution_zeroQuantity_isOk() {
      when(stockExecutionRepository.findNetQuantityPerAccountAndStock())
          .thenReturn(Collections.emptyList());
      when(stockHoldingRepository.findAll()).thenReturn(List.of(buildHolding(1L, "005930", 0)));

      assertThat(reconciliationService.checkHoldingConsistency()).isZero();
    }

    @Test
    @DisplayName("체결 기록과 보유 목록이 모두 없으면 0 반환")
    void noDataAtAll_returnsZero() {
      when(stockExecutionRepository.findNetQuantityPerAccountAndStock())
          .thenReturn(Collections.emptyList());
      when(stockHoldingRepository.findAll()).thenReturn(Collections.emptyList());

      assertThat(reconciliationService.checkHoldingConsistency()).isZero();
    }

    @Test
    @DisplayName("여러 계좌-종목 조합 중 일부만 불일치하는 경우 불일치 건수만 반환한다")
    void partialMismatch_returnsCorrectCount() {
      // accountId=1, 005930: execNet=10, holding=10 → 일치
      // accountId=1, 000660: execNet=5,  holding=3  → 불일치
      when(stockExecutionRepository.findNetQuantityPerAccountAndStock())
          .thenReturn(rowList(executionRow(1L, "005930", 10L), executionRow(1L, "000660", 5L)));
      when(stockHoldingRepository.findAll())
          .thenReturn(List.of(buildHolding(1L, "005930", 10), buildHolding(1L, "000660", 3)));

      assertThat(reconciliationService.checkHoldingConsistency()).isEqualTo(1);
    }
  }

  // ── checkOrderStatusConsistency ───────────────────────────────────────────

  @Nested
  @DisplayName("checkOrderStatusConsistency — 주문 상태-수량 정합성")
  class CheckOrderStatusConsistency {

    @Test
    @DisplayName("불일치 없으면 0 반환")
    void noMismatch_returnsZero() {
      when(stockOrderRepository.findOrdersWithStatusInconsistency(
              OrderStatus.FILLED, OrderStatus.PARTIAL_FILLED, OrderStatus.REQUESTED))
          .thenReturn(Collections.emptyList());

      assertThat(reconciliationService.checkOrderStatusConsistency()).isZero();
    }

    @Test
    @DisplayName("상태-수량 불일치 1건이면 1 반환")
    void oneMismatch_returnsOne() {
      when(stockOrderRepository.findOrdersWithStatusInconsistency(
              OrderStatus.FILLED, OrderStatus.PARTIAL_FILLED, OrderStatus.REQUESTED))
          .thenReturn(List.of(buildOrder(10L)));

      assertThat(reconciliationService.checkOrderStatusConsistency()).isEqualTo(1);
    }
  }

  // ── checkCashBalance ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("checkCashBalance — 예수금 음수 탐지")
  class CheckCashBalance {

    @Test
    @DisplayName("예수금 음수 계좌 없으면 0 반환")
    void noNegativeCash_returnsZero() {
      when(securitiesAccountRepository.findAccountsWithNegativeCash())
          .thenReturn(Collections.emptyList());

      assertThat(reconciliationService.checkCashBalance()).isZero();
    }

    @Test
    @DisplayName("예수금 음수 계좌가 있으면 해당 건수 반환")
    void hasNegativeCash_returnsCount() {
      when(securitiesAccountRepository.findAccountsWithNegativeCash())
          .thenReturn(List.of(buildAccount(1L, new BigDecimal("-1000"))));

      assertThat(reconciliationService.checkCashBalance()).isEqualTo(1);
    }
  }

  // ── checkHoldingIntegrity ─────────────────────────────────────────────────

  @Nested
  @DisplayName("checkHoldingIntegrity — 보유수량 음수 탐지")
  class CheckHoldingIntegrity {

    @Test
    @DisplayName("음수 보유수량 없으면 0 반환")
    void noNegativeHolding_returnsZero() {
      when(stockHoldingRepository.findHoldingsWithNegativeQuantity())
          .thenReturn(Collections.emptyList());

      assertThat(reconciliationService.checkHoldingIntegrity()).isZero();
    }

    @Test
    @DisplayName("음수 보유수량 종목이 있으면 해당 건수 반환")
    void hasNegativeHolding_returnsCount() {
      StockHolding badHolding = buildHolding(1L, "005930", -5);
      when(stockHoldingRepository.findHoldingsWithNegativeQuantity())
          .thenReturn(List.of(badHolding));

      assertThat(reconciliationService.checkHoldingIntegrity()).isEqualTo(1);
    }
  }
}
