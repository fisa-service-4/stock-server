package com.stock.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AccountValidateRequest {

  @NotBlank private String toBankCode;

  @NotBlank private String toAccountNumber;
}
