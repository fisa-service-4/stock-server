package com.stock.domain.portfolio.scheduler;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.portfolio.service.PortfolioSnapshotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotScheduler {

  private final SecuritiesAccountRepository accountRepository;
  private final PortfolioSnapshotService snapshotService;

  @Scheduled(cron = "0 0 0 * * *")
  public void dailySnapshot() {
    List<SecuritiesAccount> accounts =
        accountRepository.findAllByAccountStatus(AccountStatus.ACTIVE);

    log.info("포트폴리오 스냅샷 스케줄러 시작 — 대상 계좌 수={}", accounts.size());
    int success = 0;
    int failed = 0;

    for (SecuritiesAccount account : accounts) {
      try {
        snapshotService.takeSnapshot(
            account.getUserId(), account.getSecuritiesAccountId());
        success++;
      } catch (Exception e) {
        log.error(
            "스냅샷 저장 실패 accountId={}", account.getSecuritiesAccountId(), e);
        failed++;
      }
    }

    log.info(
        "포트폴리오 스냅샷 스케줄러 완료 — success={} failed={}", success, failed);
  }
}
