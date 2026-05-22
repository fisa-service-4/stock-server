package com.stock.domain.account.dto.response;

import com.stock.domain.account.entity.SecuritiesAccount;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CashBalanceResponse {

  private Long accountId;
  private BigDecimal cashBalance;
  private BigDecimal availableBalance;

  public static CashBalanceResponse from(SecuritiesAccount account) {
    return CashBalanceResponse.builder()
        .accountId(account.getSecuritiesAccountId())
        .cashBalance(account.getCashBalance())
        .availableBalance(account.getWithdrawableBalance())
        .build();
  }
}
