package com.stock.domain.account.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stock.domain.account.entity.enums.AccountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SecuritiesAccountTest {

  private SecuritiesAccount account(BigDecimal cashBalance) {
    return SecuritiesAccount.builder()
        .userId(1L)
        .brokerCode("243")
        .accountNumber("300-123-456789")
        .accountName("테스트 계좌")
        .cashBalance(cashBalance)
        .withdrawableBalance(cashBalance)
        .accountStatus(AccountStatus.ACTIVE)
        .openedAt(LocalDateTime.now())
        .build();
  }

  @Nested
  @DisplayName("deposit() — 입금")
  class Deposit {

    @Test
    @DisplayName("입금 후 cashBalance와 withdrawableBalance가 amount만큼 증가한다")
    void deposit_increasesBothBalances() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));

      acc.deposit(new BigDecimal("500000"));

      assertThat(acc.getCashBalance()).isEqualByComparingTo(new BigDecimal("1500000"));
      assertThat(acc.getWithdrawableBalance()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    @DisplayName("잔액이 0인 계좌에 입금하면 amount가 그대로 반영된다")
    void deposit_fromZeroBalance() {
      SecuritiesAccount acc = account(BigDecimal.ZERO);

      acc.deposit(new BigDecimal("300000"));

      assertThat(acc.getCashBalance()).isEqualByComparingTo(new BigDecimal("300000"));
    }
  }

  @Nested
  @DisplayName("withdraw() — 출금")
  class Withdraw {

    @Test
    @DisplayName("출금 후 cashBalance와 withdrawableBalance가 amount만큼 감소한다")
    void withdraw_decreasesBothBalances() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));

      acc.withdraw(new BigDecimal("300000"));

      assertThat(acc.getCashBalance()).isEqualByComparingTo(new BigDecimal("700000"));
      assertThat(acc.getWithdrawableBalance()).isEqualByComparingTo(new BigDecimal("700000"));
    }

    @Test
    @DisplayName("잔액(1,000,000)과 출금액(1,000,000)이 같으면 잔액이 0이 된다 (경계값)")
    void withdraw_exactBalance_resultZero() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));

      acc.withdraw(new BigDecimal("1000000"));

      assertThat(acc.getCashBalance()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(acc.getWithdrawableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }
}
