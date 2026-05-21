package com.stock.domain.portfolio.entity;

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
@Table(name = "stock_portfolio_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockPortfolioSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "snapshot_id")
  private Long snapshotId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "total_asset", precision = 18, scale = 2)
  private BigDecimal totalAsset;

  @Column(name = "stock_asset", precision = 18, scale = 2)
  private BigDecimal stockAsset;

  @Column(name = "cash_asset", precision = 18, scale = 2)
  private BigDecimal cashAsset;

  @Column(name = "total_profit", precision = 18, scale = 2)
  private BigDecimal totalProfit;

  @Column(name = "total_profit_rate", precision = 5, scale = 2)
  private BigDecimal totalProfitRate;

  @Column(name = "snapshot_date", nullable = false)
  private LocalDate snapshotDate;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Builder
  public StockPortfolioSnapshot(
      Long userId,
      BigDecimal totalAsset,
      BigDecimal stockAsset,
      BigDecimal cashAsset,
      BigDecimal totalProfit,
      BigDecimal totalProfitRate,
      LocalDate snapshotDate,
      LocalDateTime createdAt) {
    this.userId = userId;
    this.totalAsset = totalAsset;
    this.stockAsset = stockAsset;
    this.cashAsset = cashAsset;
    this.totalProfit = totalProfit;
    this.totalProfitRate = totalProfitRate;
    this.snapshotDate = snapshotDate;
    this.createdAt = createdAt;
  }
}
