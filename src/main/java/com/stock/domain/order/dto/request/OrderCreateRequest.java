package com.stock.domain.order.dto.request;

import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderCreateRequest {

  @NotBlank private String stockCode;

  @NotNull private OrderType orderType;

  @NotNull private OrderMethod orderMethod;

  @NotNull
  @Min(1)
  private Integer quantity;

  private BigDecimal price;
}
