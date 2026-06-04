package com.stock.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CashRequest {

  @NotBlank(message = "계좌번호는 필수입니다.")
  private String accountNumber;

  @NotNull(message = "금액은 필수입니다.")
  @Positive(message = "금액은 0보다 커야 합니다.")
  private BigDecimal amount;

  private Long sagaId;
}
