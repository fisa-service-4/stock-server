package com.stock.domain.account.dto.response;

import com.stock.domain.account.entity.SecuritiesAccount;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CashBalanceResponse {

  private Long accountId;
  private BigDecimal availableCash;
  private BigDecimal withdrawableAmount;

  public static CashBalanceResponse from(SecuritiesAccount account) {
    return CashBalanceResponse.builder()
        .accountId(account.getSecuritiesAccountId())
        .availableCash(account.getCashBalance())
        .withdrawableAmount(account.getWithdrawableBalance())
        .build();
  }
}
