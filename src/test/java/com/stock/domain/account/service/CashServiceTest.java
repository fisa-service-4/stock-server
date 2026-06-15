package com.stock.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.stock.domain.account.dto.request.CashRequest;
import com.stock.domain.account.dto.response.CashResponse;
import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

  @Mock private SecuritiesAccountRepository securitiesAccountRepository;

  @InjectMocks private CashService cashService;

  private static final Long USER_ID = 1L;
  private static final String ACCOUNT_NUMBER = "300-123-456789";

  private SecuritiesAccount account(BigDecimal cashBalance) {
    SecuritiesAccount acc =
        SecuritiesAccount.builder()
            .userId(USER_ID)
            .brokerCode("243")
            .accountNumber(ACCOUNT_NUMBER)
            .accountName("테스트 계좌")
            .cashBalance(cashBalance)
            .withdrawableBalance(cashBalance)
            .accountStatus(AccountStatus.ACTIVE)
            .openedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(acc, "securitiesAccountId", 1L);
    return acc;
  }

  private CashRequest cashRequest(String accountNumber, BigDecimal amount) {
    CashRequest req = new CashRequest();
    ReflectionTestUtils.setField(req, "accountNumber", accountNumber);
    ReflectionTestUtils.setField(req, "amount", amount);
    return req;
  }

  // ──────────────────────────────────────────────
  // deposit
  // ──────────────────────────────────────────────

  @Nested
  @DisplayName("deposit() — 예수금 입금")
  class Deposit {

    @Test
    @DisplayName("정상 입금: cashBalance가 amount만큼 증가한다")
    void deposit_success() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.of(acc));

      CashResponse response =
          cashService.deposit(USER_ID, cashRequest(ACCOUNT_NUMBER, new BigDecimal("500000")));

      assertThat(response.getCashBalance()).isEqualByComparingTo(new BigDecimal("1500000"));
      assertThat(acc.getCashBalance()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    @DisplayName("계좌 없음: ACCOUNT_001 발생")
    void deposit_accountNotFound_throws_ACCOUNT_001() {
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  cashService.deposit(
                      USER_ID, cashRequest(ACCOUNT_NUMBER, new BigDecimal("500000"))))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e ->
                  assertThat(((GlobalException) e).getErrorCode())
                      .isEqualTo(ErrorCode.ACCOUNT_001));
    }

    @Test
    @DisplayName("타인 계좌: ACCOUNT_002 발생")
    void deposit_wrongOwner_throws_ACCOUNT_002() {
      SecuritiesAccount acc = account(new BigDecimal("1000000")); // userId = USER_ID(1)
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.of(acc));

      Long otherUserId = 999L;
      assertThatThrownBy(
              () ->
                  cashService.deposit(
                      otherUserId, cashRequest(ACCOUNT_NUMBER, new BigDecimal("500000"))))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e ->
                  assertThat(((GlobalException) e).getErrorCode())
                      .isEqualTo(ErrorCode.ACCOUNT_002));
    }
  }

  // ──────────────────────────────────────────────
  // withdraw
  // ──────────────────────────────────────────────

  @Nested
  @DisplayName("withdraw() — 예수금 출금")
  class Withdraw {

    @Test
    @DisplayName("정상 출금: cashBalance가 amount만큼 감소한다")
    void withdraw_success() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.of(acc));

      CashResponse response =
          cashService.withdraw(USER_ID, cashRequest(ACCOUNT_NUMBER, new BigDecimal("300000")));

      assertThat(response.getCashBalance()).isEqualByComparingTo(new BigDecimal("700000"));
    }

    @Test
    @DisplayName("잔액(1,000,000) == 출금액(1,000,000): 성공하고 잔액이 0이 된다 (경계값)")
    void withdraw_exactBalance_succeeds() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.of(acc));

      CashResponse response =
          cashService.withdraw(USER_ID, cashRequest(ACCOUNT_NUMBER, new BigDecimal("1000000")));

      assertThat(response.getCashBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("잔액(500,000) < 출금액(1,000,000): TRANSFER_002 발생")
    void withdraw_insufficientBalance_throws_TRANSFER_002() {
      SecuritiesAccount acc = account(new BigDecimal("500000"));
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.of(acc));

      assertThatThrownBy(
              () ->
                  cashService.withdraw(
                      USER_ID, cashRequest(ACCOUNT_NUMBER, new BigDecimal("1000000"))))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e ->
                  assertThat(((GlobalException) e).getErrorCode())
                      .isEqualTo(ErrorCode.TRANSFER_002));
    }

    @Test
    @DisplayName("계좌 없음: ACCOUNT_001 발생")
    void withdraw_accountNotFound_throws_ACCOUNT_001() {
      when(securitiesAccountRepository.findByAccountNumber(ACCOUNT_NUMBER))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  cashService.withdraw(
                      USER_ID, cashRequest(ACCOUNT_NUMBER, new BigDecimal("300000"))))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e ->
                  assertThat(((GlobalException) e).getErrorCode())
                      .isEqualTo(ErrorCode.ACCOUNT_001));
    }
  }
}
