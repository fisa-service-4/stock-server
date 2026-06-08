package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockPriceHistory;
import java.math.RoundingMode;
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
    private Long open;
    private Long high;
    private Long low;
    private Long close;
    private Long volume;

    public static CandleItem from(StockPriceHistory history) {
      return CandleItem.builder()
          .date(history.getTradedDate())
          .open(history.getOpenPrice().setScale(0, RoundingMode.HALF_UP).longValue())
          .high(history.getHighPrice().setScale(0, RoundingMode.HALF_UP).longValue())
          .low(history.getLowPrice().setScale(0, RoundingMode.HALF_UP).longValue())
          .close(history.getClosePrice().setScale(0, RoundingMode.HALF_UP).longValue())
          .volume(history.getVolume())
          .build();
    }
  }
}
