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
public class OrderDetailResponse {

  private Long orderId;
  private Long accountId;
  private String stockCode;
  private String stockName;
  private OrderType orderType;
  private OrderMethod orderMethod;
  private Integer quantity;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private BigDecimal price;
  private BigDecimal averageExecutionPrice;
  private OrderStatus status;
  private LocalDateTime orderedAt;
  private LocalDateTime updatedAt;

  public static OrderDetailResponse from(StockOrder order, String stockName) {
    return OrderDetailResponse.builder()
        .orderId(order.getStockOrderId())
        .accountId(order.getSecuritiesAccountId())
        .stockCode(order.getStockCode())
        .stockName(stockName)
        .orderType(order.getOrderType())
        .orderMethod(order.getOrderMethod())
        .quantity(order.getOrderQuantity())
        .filledQuantity(order.getFilledQuantity())
        .remainingQuantity(order.getRemainingQuantity())
        .price(order.getOrderPrice())
        .averageExecutionPrice(order.getAverageExecutionPrice())
        .status(order.getOrderStatus())
        .orderedAt(order.getOrderedAt())
        .updatedAt(order.getUpdatedAt())
        .build();
  }
}
