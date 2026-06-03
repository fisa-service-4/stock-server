package com.stock.domain.account.service;

import com.stock.domain.account.dto.request.CashRequest;
import com.stock.domain.account.dto.response.CashResponse;
import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.validator.AccountValidator;
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

  private final AccountValidator accountValidator;

  @Transactional
  public CashResponse deposit(Long userId, Long accountId, CashRequest request) {
    SecuritiesAccount account = accountValidator.validateOwner(userId, accountId);
    account.deposit(request.getAmount());
    log.info(
        "[{}] 예수금 입금 완료 accountId={} amount={} sagaId={}",
        MDC.get("traceId"),
        accountId,
        request.getAmount(),
        request.getSagaId());
    return CashResponse.from(account);
  }

  @Transactional
  public CashResponse withdraw(Long userId, Long accountId, CashRequest request) {
    SecuritiesAccount account = accountValidator.validateOwner(userId, accountId);

    if (account.getCashBalance().compareTo(request.getAmount()) < 0) {
      log.warn(
          "[{}] 예수금 부족 accountId={} balance={} amount={}",
          MDC.get("traceId"),
          accountId,
          account.getCashBalance(),
          request.getAmount());
      throw new GlobalException(ErrorCode.TRANSFER_002);
    }

    account.withdraw(request.getAmount());
    log.info(
        "[{}] 예수금 출금 완료 accountId={} amount={} sagaId={}",
        MDC.get("traceId"),
        accountId,
        request.getAmount(),
        request.getSagaId());
    return CashResponse.from(account);
  }
}
