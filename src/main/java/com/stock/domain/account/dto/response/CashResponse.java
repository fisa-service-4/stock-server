package com.stock.domain.account.dto.response;

import com.stock.domain.account.entity.SecuritiesAccount;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CashResponse {

  private Long accountId;
  private BigDecimal cashBalance;

  public static CashResponse from(SecuritiesAccount account) {
    return CashResponse.builder()
        .accountId(account.getSecuritiesAccountId())
        .cashBalance(account.getCashBalance())
        .build();
  }
}
