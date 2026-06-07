package com.stock.domain.stock.scheduler;

import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.external.kis.provider.StockPriceProvider;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockPriceScheduler {

  private final StockMasterRepository stockMasterRepository;
  private final StockPriceProvider stockPriceProvider;
  private final StockPriceHistoryService stockPriceHistoryService;

  @Value("${kis.call-delay-ms:100}")
  private long callDelayMs;

  @Scheduled(fixedDelayString = "${stock.mock.tick-interval}")
  public void collectPrices() {
    log.info("[StockPriceScheduler] 시세 스케줄러 실행 시작");

    List<StockMaster> stocks = stockMasterRepository.findAll();
    int processed = 0;

    for (int i = 0; i < stocks.size(); i++) {
      StockMaster stock = stocks.get(i);
      try {
        String stockCode = stock.getStockCode();
        BigDecimal nextPrice = stockPriceProvider.getCurrentPrice(stockCode);
        stockPriceHistoryService.recordTick(stockCode, nextPrice);
        processed++;

        if (callDelayMs > 0 && i < stocks.size() - 1) {
          Thread.sleep(callDelayMs);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("[StockPriceScheduler] 인터럽트 발생 stockCode={}", stock.getStockCode());
        break;
      } catch (Exception e) {
        log.error(
            "[StockPriceScheduler] 시세 저장 실패 stockCode={} message={}",
            stock.getStockCode(),
            e.getMessage());
      }
    }

    log.info("[StockPriceScheduler] 시세 스케줄러 실행 완료 processed={}", processed);
  }
}
