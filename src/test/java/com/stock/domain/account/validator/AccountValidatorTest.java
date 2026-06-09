package com.stock.domain.account.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountValidatorTest {

  @Mock private SecuritiesAccountRepository securitiesAccountRepository;

  @InjectMocks private AccountValidator accountValidator;

  private static final Long USER_ID = 1L;
  private static final Long ACCOUNT_ID = 100L;

  private SecuritiesAccount account(Long userId) {
    SecuritiesAccount acc =
        SecuritiesAccount.builder()
            .userId(userId)
            .brokerCode("243")
            .accountNumber("300-123-456789")
            .accountName("테스트 계좌")
            .cashBalance(new BigDecimal("1000000"))
            .withdrawableBalance(new BigDecimal("1000000"))
            .accountStatus(AccountStatus.ACTIVE)
            .openedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(acc, "securitiesAccountId", ACCOUNT_ID);
    return acc;
  }

  @Test
  @DisplayName("계좌 존재 + 소유자 일치: SecuritiesAccount 반환")
  void validateOwner_success_returnsAccount() {
    SecuritiesAccount acc = account(USER_ID);
    when(securitiesAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acc));

    SecuritiesAccount result = accountValidator.validateOwner(USER_ID, ACCOUNT_ID);

    assertThat(result).isEqualTo(acc);
    assertThat(result.getUserId()).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("계좌 없음: ACCOUNT_001 발생")
  void validateOwner_accountNotFound_throws_ACCOUNT_001() {
    when(securitiesAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> accountValidator.validateOwner(USER_ID, ACCOUNT_ID))
        .isInstanceOf(GlobalException.class)
        .satisfies(
            e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_001));
  }

  @Test
  @DisplayName("타인 계좌 (userId 불일치): ACCOUNT_002 발생")
  void validateOwner_wrongOwner_throws_ACCOUNT_002() {
    SecuritiesAccount acc = account(USER_ID); // userId = 1
    when(securitiesAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acc));

    Long otherUserId = 999L;
    assertThatThrownBy(() -> accountValidator.validateOwner(otherUserId, ACCOUNT_ID))
        .isInstanceOf(GlobalException.class)
        .satisfies(
            e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_002));
  }
}
