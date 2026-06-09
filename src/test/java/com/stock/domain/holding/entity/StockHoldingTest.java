package com.stock.domain.holding.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StockHoldingTest {

  private StockHolding holding(int quantity, BigDecimal avgPrice) {
    return StockHolding.builder()
        .securitiesAccountId(1L)
        .stockCode("005930")
        .holdingQuantity(quantity)
        .averagePurchasePrice(avgPrice)
        .totalPurchaseAmount(avgPrice.multiply(BigDecimal.valueOf(quantity)))
        .build();
  }

  @Nested
  @DisplayName("buy() — 추가 매수 시 평단가·수량·총매입금액 갱신")
  class Buy {

    @Test
    @DisplayName("10주@10,000 보유 + 20주@20,000 추가 매수 → 30주, 평단가 16,667 (HALF_UP)")
    void buy_recalculatesAvgPrice() {
      StockHolding holding = holding(10, new BigDecimal("10000"));

      holding.buy(20, new BigDecimal("20000"));

      // (10*10,000 + 20*20,000) / 30 = 500,000 / 30 = 16,666.67 → 16,667
      assertThat(holding.getHoldingQuantity()).isEqualTo(30);
      assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo(new BigDecimal("16667"));
    }

    @Test
    @DisplayName("평단가가 소수점 없이 딱 떨어지는 경우: 10주@10,000 + 10주@20,000 → 20주, 평단가 15,000")
    void buy_evenAvgPrice() {
      StockHolding holding = holding(10, new BigDecimal("10000"));

      holding.buy(10, new BigDecimal("20000"));

      assertThat(holding.getHoldingQuantity()).isEqualTo(20);
      assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo(new BigDecimal("15000"));
    }

    @Test
    @DisplayName("totalPurchaseAmount = avgPrice × newQuantity")
    void buy_updatesTotalPurchaseAmount() {
      StockHolding holding = holding(10, new BigDecimal("10000"));

      holding.buy(20, new BigDecimal("20000"));

      BigDecimal expected = holding.getAveragePurchasePrice()
          .multiply(BigDecimal.valueOf(holding.getHoldingQuantity()));
      assertThat(holding.getTotalPurchaseAmount()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("price에 소수점이 있어도 HALF_UP으로 반올림된다 (33,333.33 → 33,333)")
    void buy_roundsInputPriceHalfUp() {
      StockHolding holding = holding(1, new BigDecimal("33334"));

      holding.buy(2, new BigDecimal("33333.33"));

      // price = 33,333 (HALF_UP from 33,333.33)
      // (1*33,334 + 2*33,333) / 3 = 99,000 / 3 = 33,000
      assertThat(holding.getHoldingQuantity()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("sell() — 매도 시 수량·총매입금액 갱신")
  class Sell {

    @Test
    @DisplayName("20주 보유 중 10주 매도 → 10주 남음")
    void sell_partialQuantity() {
      StockHolding holding = holding(20, new BigDecimal("60000"));

      holding.sell(10);

      assertThat(holding.getHoldingQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("매도 후 totalPurchaseAmount = avgPrice × 남은수량")
    void sell_updatesTotalPurchaseAmount() {
      StockHolding holding = holding(20, new BigDecimal("60000"));

      holding.sell(10);

      // 60,000 * 10 = 600,000
      assertThat(holding.getTotalPurchaseAmount()).isEqualByComparingTo(new BigDecimal("600000"));
    }

    @Test
    @DisplayName("전량(20주) 매도 → quantity=0, totalPurchaseAmount=0")
    void sell_allQuantity_resultZero() {
      StockHolding holding = holding(20, new BigDecimal("60000"));

      holding.sell(20);

      assertThat(holding.getHoldingQuantity()).isEqualTo(0);
      assertThat(holding.getTotalPurchaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }
}
