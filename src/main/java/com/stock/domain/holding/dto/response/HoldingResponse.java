package com.stock.domain.holding.dto.response;

import com.stock.domain.holding.entity.StockHolding;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HoldingResponse {

  private String stockCode;
  private String stockName;
  private Integer quantity;
  private BigDecimal averagePrice;
  private BigDecimal currentPrice;
  private BigDecimal evaluationAmount;
  private BigDecimal unrealizedProfit;
  private BigDecimal profitRate;

  public static HoldingResponse of(
      StockHolding holding, String stockName, BigDecimal currentPrice) {
    BigDecimal qty = BigDecimal.valueOf(holding.getHoldingQuantity());
    BigDecimal evaluationAmount = currentPrice.multiply(qty);
    BigDecimal totalPurchased =
        holding.getTotalPurchaseAmount() != null
            ? holding.getTotalPurchaseAmount()
            : holding.getAveragePurchasePrice().multiply(qty);
    BigDecimal unrealizedProfit = evaluationAmount.subtract(totalPurchased);
    BigDecimal profitRate =
        totalPurchased.compareTo(BigDecimal.ZERO) > 0
            ? unrealizedProfit
                .divide(totalPurchased, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return HoldingResponse.builder()
        .stockCode(holding.getStockCode())
        .stockName(stockName)
        .quantity(holding.getHoldingQuantity())
        .averagePrice(holding.getAveragePurchasePrice())
        .currentPrice(currentPrice)
        .evaluationAmount(evaluationAmount.setScale(2, RoundingMode.HALF_UP))
        .unrealizedProfit(unrealizedProfit.setScale(2, RoundingMode.HALF_UP))
        .profitRate(profitRate)
        .build();
  }
}
