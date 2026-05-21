package com.stock.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_price_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockPriceHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "price_history_id")
  private Long priceHistoryId;

  @Column(name = "stock_code", length = 20, nullable = false)
  private String stockCode;

  @Column(name = "traded_date", nullable = false)
  private LocalDate tradedDate;

  @Column(name = "open_price", precision = 18, scale = 2)
  private BigDecimal openPrice;

  @Column(name = "high_price", precision = 18, scale = 2)
  private BigDecimal highPrice;

  @Column(name = "low_price", precision = 18, scale = 2)
  private BigDecimal lowPrice;

  @Column(name = "close_price", precision = 18, scale = 2)
  private BigDecimal closePrice;

  @Column(name = "volume")
  private Long volume;

  @Column(name = "fluctuation_rate", precision = 5, scale = 2)
  private BigDecimal fluctuationRate;

  @Column(name = "market_cap", precision = 18, scale = 2)
  private BigDecimal marketCap;

  @Column(name = "collected_at", nullable = false)
  private LocalDateTime collectedAt;

  @Builder
  public StockPriceHistory(
      String stockCode,
      LocalDate tradedDate,
      BigDecimal openPrice,
      BigDecimal highPrice,
      BigDecimal lowPrice,
      BigDecimal closePrice,
      Long volume,
      BigDecimal fluctuationRate,
      BigDecimal marketCap,
      LocalDateTime collectedAt) {
    this.stockCode = stockCode;
    this.tradedDate = tradedDate;
    this.openPrice = openPrice;
    this.highPrice = highPrice;
    this.lowPrice = lowPrice;
    this.closePrice = closePrice;
    this.volume = volume;
    this.fluctuationRate = fluctuationRate;
    this.marketCap = marketCap;
    this.collectedAt = collectedAt;
  }
}
