package com.stock.domain.account.service;

import com.stock.domain.account.dto.request.AccountValidateRequest;
import com.stock.domain.account.dto.response.AccountResponse;
import com.stock.domain.account.dto.response.AccountValidateResponse;
import com.stock.domain.account.dto.response.CashBalanceResponse;
import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.account.validator.AccountValidator;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final AccountValidator accountValidator;

  @Transactional(readOnly = true)
  public List<AccountResponse> getAccounts(Long userId) {
    List<AccountResponse> accounts =
        securitiesAccountRepository.findByUserId(userId).stream()
            .map(AccountResponse::from)
            .toList();

    if (accounts.isEmpty()) {
      log.warn("[{}] [userId={}] 계좌 없음", MDC.get("traceId"), userId);
      throw new GlobalException(ErrorCode.ACCOUNT_001);
    }

    log.info("[{}] [userId={}] 계좌 조회 완료 count={}", MDC.get("traceId"), userId, accounts.size());
    return accounts;
  }

  @Transactional(readOnly = true)
  public CashBalanceResponse getCashBalance(Long userId, Long accountId) {
    var account = accountValidator.validateOwner(userId, accountId);
    log.info("[{}] 예수금 조회 완료 accountId={}", MDC.get("traceId"), accountId);
    return CashBalanceResponse.from(account);
  }

  @Transactional(readOnly = true)
  public AccountValidateResponse validateAccount(AccountValidateRequest request) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByBrokerCodeAndAccountNumber(request.getToBankCode(), request.getToAccountNumber())
            .orElseThrow(
                () -> {
                  log.warn(
                      "[{}] 계좌 없음 brokerCode={} accountNumber={}",
                      MDC.get("traceId"),
                      request.getToBankCode(),
                      request.getToAccountNumber());
                  return new GlobalException(ErrorCode.ACCOUNT_001);
                });

    if (account.getAccountStatus() == AccountStatus.LOCKED
        || account.getAccountStatus() == AccountStatus.CLOSED) {
      log.warn(
          "[{}] 사용 불가 계좌 accountNumber={} status={}",
          MDC.get("traceId"),
          request.getToAccountNumber(),
          account.getAccountStatus());
      throw new GlobalException(ErrorCode.ACCOUNT_003);
    }

    log.info(
        "[{}] 계좌 유효성 검증 완료 accountNumber={} status={}",
        MDC.get("traceId"),
        request.getToAccountNumber(),
        account.getAccountStatus());
    return AccountValidateResponse.from(account);
  }
}
