package com.stock.domain.order.entity;

import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.entity.enums.OrderedBy;
import com.stock.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "stock_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockOrder extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "stock_order_id")
  private Long stockOrderId;

  @Column(name = "securities_account_id", nullable = false)
  private Long securitiesAccountId;

  @Column(name = "stock_code", length = 20, nullable = false)
  private String stockCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", length = 10, nullable = false)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_method", length = 10, nullable = false)
  private OrderMethod orderMethod;

  @Column(name = "order_price", precision = 18, scale = 2)
  private BigDecimal orderPrice;

  @Column(name = "order_quantity", nullable = false)
  private Integer orderQuantity;

  @Column(name = "filled_quantity")
  private Integer filledQuantity;

  @Column(name = "remaining_quantity")
  private Integer remainingQuantity;

  @Column(name = "average_execution_price", precision = 18, scale = 2)
  private BigDecimal averageExecutionPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_status", length = 20, nullable = false)
  private OrderStatus orderStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "ordered_by", length = 10, nullable = false)
  private OrderedBy orderedBy;

  @Column(name = "ordered_at", nullable = false)
  private LocalDateTime orderedAt;

  @Column(name = "idempotency_key", length = 255, unique = true)
  private String idempotencyKey;

  @Builder
  public StockOrder(
      Long securitiesAccountId,
      String stockCode,
      OrderType orderType,
      OrderMethod orderMethod,
      BigDecimal orderPrice,
      Integer orderQuantity,
      OrderedBy orderedBy,
      LocalDateTime orderedAt,
      String idempotencyKey) {
    this.securitiesAccountId = securitiesAccountId;
    this.stockCode = stockCode;
    this.orderType = orderType;
    this.orderMethod = orderMethod;
    this.orderPrice = orderPrice;
    this.orderQuantity = orderQuantity;
    this.filledQuantity = 0;
    this.remainingQuantity = orderQuantity;
    this.orderStatus = OrderStatus.REQUESTED;
    this.orderedBy = orderedBy;
    this.orderedAt = orderedAt;
    this.idempotencyKey = idempotencyKey;
  }

  public void fill(int executedQuantity, BigDecimal executionPrice) {
    this.filledQuantity = (this.filledQuantity == null ? 0 : this.filledQuantity) + executedQuantity;
    this.remainingQuantity = this.orderQuantity - this.filledQuantity;
    this.averageExecutionPrice = executionPrice;
    this.orderStatus =
        this.remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PARTIAL_FILLED;
  }

  public void cancel() {
    this.orderStatus = OrderStatus.CANCELLED;
    this.remainingQuantity = 0;
  }
}
