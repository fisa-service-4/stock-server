package com.stock.domain.order.dto.response;

import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCreateResponse {

  private Long orderId;
  private String stockCode;
  private OrderType orderType;
  private OrderMethod orderMethod;
  private Integer quantity;
  private BigDecimal price;
  private BigDecimal averageExecutionPrice;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private OrderStatus status;
  private LocalDateTime orderedAt;

  public static OrderCreateResponse from(StockOrder order) {
    BigDecimal price =
        order.getOrderPrice() != null ? order.getOrderPrice() : order.getAverageExecutionPrice();
    return OrderCreateResponse.builder()
        .orderId(order.getStockOrderId())
        .stockCode(order.getStockCode())
        .orderType(order.getOrderType())
        .orderMethod(order.getOrderMethod())
        .quantity(order.getOrderQuantity())
        .price(price)
        .averageExecutionPrice(order.getAverageExecutionPrice())
        .filledQuantity(order.getFilledQuantity())
        .remainingQuantity(order.getRemainingQuantity())
        .status(order.getOrderStatus())
        .orderedAt(order.getOrderedAt())
        .build();
  }
}
