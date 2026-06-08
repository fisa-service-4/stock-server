package com.stock.domain.account.service;

import com.stock.domain.account.dto.request.CashRequest;
import com.stock.domain.account.dto.response.CashResponse;
import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashService {

  private final SecuritiesAccountRepository securitiesAccountRepository;

  @Transactional
  public CashResponse deposit(Long userId, CashRequest request) {
    SecuritiesAccount account = findAndValidateOwner(userId, request.getAccountNumber());
    account.deposit(request.getAmount());
    log.info(
        "[{}] 예수금 입금 완료 accountId={} amount={} sagaId={}",
        MDC.get("traceId"),
        account.getSecuritiesAccountId(),
        request.getAmount(),
        request.getSagaId());
    return CashResponse.from(account);
  }

  @Transactional
  public CashResponse withdraw(Long userId, CashRequest request) {
    SecuritiesAccount account = findAndValidateOwner(userId, request.getAccountNumber());

    if (account.getCashBalance().compareTo(request.getAmount()) < 0) {
      log.warn(
          "[{}] 예수금 부족 accountId={} balance={} amount={}",
          MDC.get("traceId"),
          account.getSecuritiesAccountId(),
          account.getCashBalance(),
          request.getAmount());
      throw new GlobalException(ErrorCode.TRANSFER_002);
    }

    account.withdraw(request.getAmount());
    log.info(
        "[{}] 예수금 출금 완료 accountId={} amount={} sagaId={}",
        MDC.get("traceId"),
        account.getSecuritiesAccountId(),
        request.getAmount(),
        request.getSagaId());
    return CashResponse.from(account);
  }

  private SecuritiesAccount findAndValidateOwner(Long userId, String accountNumber) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByAccountNumber(accountNumber)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 계좌 없음 accountNumber={}", MDC.get("traceId"), accountNumber);
                  return new GlobalException(ErrorCode.ACCOUNT_001);
                });

    if (!account.getUserId().equals(userId)) {
      log.warn(
          "[{}] 계좌 접근 권한 없음 userId={} accountNumber={}", MDC.get("traceId"), userId, accountNumber);
      throw new GlobalException(ErrorCode.ACCOUNT_002);
    }

    return account;
  }
}
