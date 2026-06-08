package com.stock.domain.reconciliation.scheduler;

import com.stock.domain.reconciliation.entity.ReconciliationResult;
import com.stock.domain.reconciliation.entity.enums.ReconciliationStatus;
import com.stock.domain.reconciliation.repository.ReconciliationResultRepository;
import com.stock.domain.reconciliation.service.ReconciliationService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

  private final ReconciliationService reconciliationService;
  private final ReconciliationResultRepository reconciliationResultRepository;

  @Scheduled(cron = "0 0 0 * * *")
  public void runDailyReconciliation() {
    LocalDateTime startedAt = LocalDateTime.now();
    LocalDate runDate = startedAt.toLocalDate();
    log.info("[정합성] 일일 정합성 검증 시작 runDate={}", runDate);

    int orderMismatch = 0;
    int holdingMismatch = 0;
    int statusMismatch = 0;
    int cashMismatch = 0;
    int holdingIntegrity = 0;

    try {
      orderMismatch = reconciliationService.checkOrderExecutionConsistency();
    } catch (Exception e) {
      log.error("[정합성] 주문-체결 수량 검증 실패", e);
    }

    try {
      holdingMismatch = reconciliationService.checkHoldingConsistency();
    } catch (Exception e) {
      log.error("[정합성] 체결-보유 수량 검증 실패", e);
    }

    try {
      statusMismatch = reconciliationService.checkOrderStatusConsistency();
    } catch (Exception e) {
      log.error("[정합성] 주문 상태-수량 검증 실패", e);
    }

    try {
      cashMismatch = reconciliationService.checkCashBalance();
    } catch (Exception e) {
      log.error("[정합성] 예수금 음수 검증 실패", e);
    }

    try {
      holdingIntegrity = reconciliationService.checkHoldingIntegrity();
    } catch (Exception e) {
      log.error("[정합성] 보유수량 음수 검증 실패", e);
    }

    LocalDateTime completedAt = LocalDateTime.now();
    long durationMs = Duration.between(startedAt, completedAt).toMillis();
    int total = orderMismatch + holdingMismatch + statusMismatch + cashMismatch + holdingIntegrity;

    ReconciliationStatus status;
    if (cashMismatch > 0 || holdingMismatch > 0) {
      status = ReconciliationStatus.ERROR;
    } else if (total > 0) {
      status = ReconciliationStatus.WARN;
    } else {
      status = ReconciliationStatus.OK;
    }

    reconciliationResultRepository.save(
        ReconciliationResult.builder()
            .runDate(runDate)
            .orderMismatchCount(orderMismatch)
            .holdingMismatchCount(holdingMismatch)
            .statusMismatchCount(statusMismatch)
            .cashMismatchCount(cashMismatch)
            .holdingIntegrityCount(holdingIntegrity)
            .totalMismatchCount(total)
            .status(status)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .durationMs(durationMs)
            .build());

    log.info(
        "[정합성] 일일 정합성 검증 완료 status={} total={} durationMs={}", status, total, durationMs);
  }
}
