package com.stock.domain.account.dto.response;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountValidateResponse {

  private boolean validYn;
  private AccountStatus status;

  public static AccountValidateResponse from(SecuritiesAccount account) {
    return AccountValidateResponse.builder()
        .validYn(true)
        .status(account.getAccountStatus())
        .build();
  }
}
