package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockPriceHistory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockChartResponse {

  private String stockCode;
  private List<Candle> candles;

  @Getter
  @Builder
  public static class Candle {

    private LocalDateTime timestamp;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    public static Candle from(StockPriceHistory history) {
      return Candle.builder()
          .timestamp(history.getCollectedAt())
          .open(history.getOpenPrice())
          .high(history.getHighPrice())
          .low(history.getLowPrice())
          .close(history.getClosePrice())
          .volume(history.getVolume())
          .build();
    }
  }
}
