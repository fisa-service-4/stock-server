package com.stock.domain.account.dto.response;

import com.stock.domain.account.entity.SecuritiesAccount;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountResponse {

  private Long accountId;
  private String accountNumber;
  private String accountName;
  private String bankCode;
  private BigDecimal cashBalance;

  public static AccountResponse from(SecuritiesAccount account) {
    return AccountResponse.builder()
        .accountId(account.getSecuritiesAccountId())
        .accountNumber(account.getAccountNumber())
        .accountName(account.getAccountName())
        .bankCode(account.getBrokerCode())
        .cashBalance(account.getCashBalance())
        .build();
  }
}
