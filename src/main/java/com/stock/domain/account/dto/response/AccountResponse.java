package com.stock.domain.account.dto.response;

import com.stock.domain.account.entity.SecuritiesAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountResponse {

  private Long accountId;
  private String accountNumber;
  private String brokerName;
  private String accountType;

  public static AccountResponse from(SecuritiesAccount account) {
    return AccountResponse.builder()
        .accountId(account.getSecuritiesAccountId())
        .accountNumber(account.getAccountNumber())
        .brokerName(account.getBrokerCode())
        .accountType(account.getAccountName())
        .build();
  }
}
