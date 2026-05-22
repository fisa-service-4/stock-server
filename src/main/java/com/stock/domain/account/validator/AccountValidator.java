package com.stock.domain.account.validator;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountValidator {

  private final SecuritiesAccountRepository securitiesAccountRepository;

  /**
   * accountId가 존재하고, 해당 계좌의 소유자가 userId인지 검증한다. 검증 통과 시 SecuritiesAccount를 반환하여 호출부 이중 조회를 방지한다.
   */
  @Transactional(readOnly = true)
  public SecuritiesAccount validateOwner(Long userId, Long accountId) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findById(accountId)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 계좌 없음 accountId={}", MDC.get("traceId"), accountId);
                  return new GlobalException(ErrorCode.ACCOUNT_001);
                });

    if (!account.getUserId().equals(userId)) {
      log.warn("[{}] 계좌 접근 권한 없음 userId={} accountId={}", MDC.get("traceId"), userId, accountId);
      throw new GlobalException(ErrorCode.ACCOUNT_002);
    }

    return account;
  }
}
