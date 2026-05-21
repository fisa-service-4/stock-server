package com.stock.domain.stock.entity;

import com.stock.domain.stock.entity.enums.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMaster {

  @Id
  @Column(name = "stock_code", length = 20, nullable = false)
  private String stockCode;

  @Column(name = "stock_name", length = 255, nullable = false)
  private String stockName;

  @Enumerated(EnumType.STRING)
  @Column(name = "market_type", length = 20, nullable = false)
  private MarketType marketType;

  @Builder
  public StockMaster(String stockCode, String stockName, MarketType marketType) {
    this.stockCode = stockCode;
    this.stockName = stockName;
    this.marketType = marketType;
  }
}
