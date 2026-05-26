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
public class OrderListItemResponse {

  private Long orderId;
  private String stockCode;
  private String stockName;
  private OrderType orderType;
  private OrderMethod orderMethod;
  private Integer quantity;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private BigDecimal price;
  private OrderStatus status;
  private LocalDateTime orderedAt;

  public static OrderListItemResponse from(StockOrder order, String stockName) {
    return OrderListItemResponse.builder()
        .orderId(order.getStockOrderId())
        .stockCode(order.getStockCode())
        .stockName(stockName)
        .orderType(order.getOrderType())
        .orderMethod(order.getOrderMethod())
        .quantity(order.getOrderQuantity())
        .filledQuantity(order.getFilledQuantity())
        .remainingQuantity(order.getRemainingQuantity())
        .price(order.getOrderPrice())
        .status(order.getOrderStatus())
        .orderedAt(order.getOrderedAt())
        .build();
  }
}
