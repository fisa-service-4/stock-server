package com.stock.domain.order.dto.response;

import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCancelResponse {

  private Long orderId;
  private OrderStatus status;
  private Integer cancelledQuantity;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private LocalDateTime cancelledAt;

  public static OrderCancelResponse from(StockOrder order, Integer cancelledQuantity) {
    return OrderCancelResponse.builder()
        .orderId(order.getStockOrderId())
        .status(order.getOrderStatus())
        .cancelledQuantity(cancelledQuantity)
        .filledQuantity(order.getFilledQuantity())
        .remainingQuantity(order.getRemainingQuantity())
        .cancelledAt(LocalDateTime.now())
        .build();
  }
}
