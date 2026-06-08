package com.stock.global.config;

import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.external.kis.dummy.provider.MockStockPriceProvider;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

  private final StockMasterRepository stockMasterRepository;
  private final StockPriceHistoryService stockPriceHistoryService;

  @Autowired(required = false)
  private MockStockPriceProvider mockStockPriceProvider;

  @PostConstruct
  public void init() {
    if (mockStockPriceProvider != null) {
      initMockMode();
    } else {
      log.info("[DataInitializer] real 모드 — 초기 시세 삽입 skip. seed 프로파일로 KIS 일봉 적재 후 기동하세요.");
    }
  }

  private void initMockMode() {
    stockMasterRepository
        .findAll()
        .forEach(
            stock -> {
              BigDecimal base = mockStockPriceProvider.getFallbackPrice(stock.getStockCode());
              stockPriceHistoryService.initDailyCandles(stock.getStockCode(), base);
              stockPriceHistoryService.initializeIfAbsent(stock.getStockCode(), base);
            });
    log.info("[DataInitializer] mock 기반 초기 시세 삽입 완료");
  }
}
