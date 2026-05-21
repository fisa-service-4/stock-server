package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import java.math.BigDecimal;
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

  public static StockPriceResponse of(StockMaster master, StockPriceHistory history) {
    return StockPriceResponse.builder()
        .stockCode(master.getStockCode())
        .stockName(master.getStockName())
        .currentPrice(history.getClosePrice())
        .changeRate(history.getFluctuationRate())
        .changeAmount(history.getClosePrice().subtract(history.getOpenPrice()))
        .volume(history.getVolume())
        .updatedAt(history.getCollectedAt())
        .build();
  }
}
