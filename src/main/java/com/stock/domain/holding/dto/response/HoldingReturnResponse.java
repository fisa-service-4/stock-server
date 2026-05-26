package com.stock.domain.holding.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HoldingReturnResponse {

  private Long accountId;
  private BigDecimal totalReturnRate;
  private BigDecimal dailyReturnRate;
}
