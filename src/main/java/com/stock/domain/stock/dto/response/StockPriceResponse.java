package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockPriceResponse {

  private String stockCode;
  private String stockName;
  private BigDecimal currentPrice;
  private BigDecimal changeRate;
  private BigDecimal changeAmount;
  private Long volume;
  private LocalDateTime updatedAt;

  public static StockPriceResponse of(
      StockMaster master, StockPriceHistory history, BigDecimal yesterdayClose) {
    BigDecimal currentPrice = history.getClosePrice();
    BigDecimal changeAmount = currentPrice.subtract(yesterdayClose);
    BigDecimal changeRate =
        yesterdayClose.compareTo(BigDecimal.ZERO) != 0
            ? changeAmount
                .divide(yesterdayClose, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return StockPriceResponse.builder()
        .stockCode(master.getStockCode())
        .stockName(master.getStockName())
        .currentPrice(currentPrice)
        .changeRate(changeRate)
        .changeAmount(changeAmount)
        .volume(history.getVolume())
        .updatedAt(history.getCollectedAt())
        .build();
  }
}
