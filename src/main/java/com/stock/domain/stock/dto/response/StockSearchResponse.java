package com.stock.domain.stock.dto.response;

import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.enums.MarketType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockSearchResponse {

  private String stockCode;
  private String stockName;
  private MarketType market;

  public static StockSearchResponse from(StockMaster stockMaster) {
    return StockSearchResponse.builder()
        .stockCode(stockMaster.getStockCode())
        .stockName(stockMaster.getStockName())
        .market(stockMaster.getMarketType())
        .build();
  }
}
