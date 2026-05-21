package com.stock.global.config;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer {

  private final SecuritiesAccountRepository securitiesAccountRepository;

  @PostConstruct
  public void init() {
    if (securitiesAccountRepository.findByUserId(1L).isEmpty()) {
      securitiesAccountRepository.save(
          SecuritiesAccount.builder()
              .userId(1L)
              .brokerCode("KIS")
              .accountNumber("1234567890")
              .accountName("테스트 계좌")
              .cashBalance(new BigDecimal("10000000"))
              .withdrawableBalance(new BigDecimal("10000000"))
              .accountStatus(AccountStatus.ACTIVE)
              .openedAt(LocalDateTime.now())
              .build());
    }
  }
}
