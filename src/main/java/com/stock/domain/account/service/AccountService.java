package com.stock.domain.account.service;

import com.stock.domain.account.dto.response.AccountResponse;
import com.stock.domain.account.dto.response.CashBalanceResponse;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
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
  public CashBalanceResponse getCashBalance(Long userId) {
    return securitiesAccountRepository
        .findFirstByUserId(userId)
        .map(account -> {
          log.info("[{}] [userId={}] 예수금 조회 완료 accountId={}",
              MDC.get("traceId"), userId, account.getSecuritiesAccountId());
          return CashBalanceResponse.from(account);
        })
        .orElseThrow(() -> {
          log.warn("[{}] [userId={}] 계좌 없음", MDC.get("traceId"), userId);
          return new GlobalException(ErrorCode.ACCOUNT_001);
        });
  }
}
