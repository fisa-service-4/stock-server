package com.stock.domain.execution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "execution_id")
  private Long executionId;

  @Column(name = "stock_order_id", nullable = false)
  private Long stockOrderId;

  @Column(name = "stock_code", length = 20, nullable = false)
  private String stockCode;

  @Column(name = "executed_price", precision = 18, scale = 2, nullable = false)
  private BigDecimal executedPrice;

  @Column(name = "executed_quantity", nullable = false)
  private Integer executedQuantity;

  @Column(name = "execution_amount", precision = 18, scale = 2, nullable = false)
  private BigDecimal executionAmount;

  @Column(name = "executed_at", nullable = false)
  private LocalDateTime executedAt;

  @Builder
  public StockExecution(
      Long stockOrderId,
      String stockCode,
      BigDecimal executedPrice,
      Integer executedQuantity,
      BigDecimal executionAmount,
      LocalDateTime executedAt) {
    this.stockOrderId = stockOrderId;
    this.stockCode = stockCode;
    this.executedPrice = executedPrice;
    this.executedQuantity = executedQuantity;
    this.executionAmount = executionAmount;
    this.executedAt = executedAt;
  }
}
