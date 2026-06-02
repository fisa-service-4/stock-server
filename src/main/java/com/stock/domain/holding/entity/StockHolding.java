package com.stock.domain.holding.entity;

import com.stock.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "stock_holding",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_holding_account_stock",
          columnNames = {"securities_account_id", "stock_code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHolding extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "holding_id")
  private Long holdingId;

  @Column(name = "securities_account_id", nullable = false)
  private Long securitiesAccountId;

  @Column(name = "stock_code", length = 20, nullable = false)
  private String stockCode;

  @Column(name = "holding_quantity", nullable = false)
  private Integer holdingQuantity;

  @Column(name = "average_purchase_price", precision = 18, scale = 2, nullable = false)
  private BigDecimal averagePurchasePrice;

  @Column(name = "total_purchase_amount", precision = 18, scale = 2)
  private BigDecimal totalPurchaseAmount;

  @Column(name = "evaluated_amount", precision = 18, scale = 2)
  private BigDecimal evaluatedAmount;

  @Column(name = "unrealized_profit", precision = 18, scale = 2)
  private BigDecimal unrealizedProfit;

  @Column(name = "profit_rate", precision = 5, scale = 2)
  private BigDecimal profitRate;

  @Builder
  public StockHolding(
      Long securitiesAccountId,
      String stockCode,
      Integer holdingQuantity,
      BigDecimal averagePurchasePrice,
      BigDecimal totalPurchaseAmount) {
    this.securitiesAccountId = securitiesAccountId;
    this.stockCode = stockCode;
    this.holdingQuantity = holdingQuantity;
    this.averagePurchasePrice = averagePurchasePrice;
    this.totalPurchaseAmount = totalPurchaseAmount;
  }

  public void buy(int quantity, BigDecimal price) {
    BigDecimal newAmount = price.multiply(BigDecimal.valueOf(quantity));
    BigDecimal totalAmount =
        this.averagePurchasePrice.multiply(BigDecimal.valueOf(this.holdingQuantity)).add(newAmount);
    int newQuantity = this.holdingQuantity + quantity;
    this.averagePurchasePrice =
        totalAmount.divide(BigDecimal.valueOf(newQuantity), 0, java.math.RoundingMode.HALF_UP);
    this.holdingQuantity = newQuantity;
    this.totalPurchaseAmount =
        this.averagePurchasePrice.multiply(BigDecimal.valueOf(this.holdingQuantity));
  }

  public void sell(int quantity) {
    this.holdingQuantity -= quantity;
    this.totalPurchaseAmount =
        this.averagePurchasePrice.multiply(BigDecimal.valueOf(this.holdingQuantity));
  }
}
