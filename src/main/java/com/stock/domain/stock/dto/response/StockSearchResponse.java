package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.entity.enums.MarketType;
import java.math.BigDecimal;
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

  public static StockSearchResponse of(StockMaster master, StockPriceHistory history) {
    return StockSearchResponse.builder()
        .stockCode(master.getStockCode())
        .stockName(master.getStockName())
        .market(master.getMarketType())
        .currentPrice(history.getClosePrice())
        .changeRate(history.getFluctuationRate())
        .build();
  }
}
