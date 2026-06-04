package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.entity.enums.MarketType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockSearchResponse {

  private String stockCode;
  private String stockName;
  private MarketType market;
  private BigDecimal currentPrice;
  private BigDecimal changeRate;

  public static StockSearchResponse of(
      StockMaster master, StockPriceHistory history, BigDecimal prevDayClose) {
    BigDecimal currentPrice = history.getClosePrice().setScale(0, RoundingMode.HALF_UP);
    BigDecimal prevClose = prevDayClose.setScale(0, RoundingMode.HALF_UP);
    BigDecimal changeRate =
        prevClose.compareTo(BigDecimal.ZERO) != 0
            ? currentPrice
                .subtract(prevClose)
                .divide(prevClose, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return StockSearchResponse.builder()
        .stockCode(master.getStockCode())
        .stockName(master.getStockName())
        .market(master.getMarketType())
        .currentPrice(currentPrice)
        .changeRate(changeRate)
        .build();
  }
}
