package com.stock.domain.execution.dto.response;

import com.stock.domain.execution.entity.StockExecution;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExecutionResponse {

  private Long executionId;
  private Long orderId;
  private String stockCode;
  private String stockName;
  private BigDecimal executedPrice;
  private Integer executedQuantity;
  private BigDecimal executionAmount;
  private LocalDateTime executedAt;

  public static ExecutionResponse from(StockExecution execution, String stockName) {
    return ExecutionResponse.builder()
        .executionId(execution.getExecutionId())
        .orderId(execution.getStockOrderId())
        .stockCode(execution.getStockCode())
        .stockName(stockName)
        .executedPrice(execution.getExecutedPrice())
        .executedQuantity(execution.getExecutedQuantity())
        .executionAmount(execution.getExecutionAmount())
        .executedAt(execution.getExecutedAt())
        .build();
  }
}
