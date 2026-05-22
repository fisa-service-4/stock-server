package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockPriceHistory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockChartResponse {

  private List<CandleItem> content;

  @Getter
  @Builder
  public static class CandleItem {

    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    public static CandleItem from(StockPriceHistory history) {
      return CandleItem.builder()
          .date(history.getTradedDate())
          .open(history.getOpenPrice())
          .high(history.getHighPrice())
          .low(history.getLowPrice())
          .close(history.getClosePrice())
          .volume(history.getVolume())
          .build();
    }
  }
}
