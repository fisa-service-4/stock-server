package com.stock.domain.stock.scheduler;

import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.external.kis.dummy.provider.MockStockPriceProvider;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockPriceScheduler {

  private final StockMasterRepository stockMasterRepository;
  private final StockPriceHistoryRepository stockPriceHistoryRepository;
  private final MockStockPriceProvider mockStockPriceProvider;
  private final StockPriceHistoryService stockPriceHistoryService;

  @Scheduled(fixedDelayString = "${stock.mock.tick-interval}")
  public void collectPrices() {
    log.info("[StockPriceScheduler] 시세 스케줄러 실행 시작");

    stockMasterRepository
        .findAll()
        .forEach(
            stock -> {
              try {
                String stockCode = stock.getStockCode();
                BigDecimal prevClose =
                    stockPriceHistoryRepository
                        .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
                        .map(history -> history.getClosePrice())
                        .orElse(mockStockPriceProvider.getFallbackPrice(stockCode));
                BigDecimal nextPrice = mockStockPriceProvider.getNextPrice(stockCode, prevClose);
                stockPriceHistoryService.recordTick(stockCode, prevClose, nextPrice);
              } catch (Exception e) {
                log.error(
                    "[StockPriceScheduler] 시세 저장 실패 stockCode={} message={}",
                    stock.getStockCode(),
                    e.getMessage());
              }
            });

    log.info("[StockPriceScheduler] 시세 스케줄러 실행 완료");
  }
}
