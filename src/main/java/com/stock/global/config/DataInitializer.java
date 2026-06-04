package com.stock.global.config;

import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.external.kis.dummy.provider.MockStockPriceProvider;
import jakarta.annotation.PostConstruct;
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
    initDailyCandles();
    initStockPrices();
  }

  private void initStockPrices() {
    if (mockStockPriceProvider == null) {
      log.info("[DataInitializer] mock 비활성화 — 초기 시세 삽입 skip");
      return;
    }
    stockMasterRepository
        .findAll()
        .forEach(
            stock ->
                stockPriceHistoryService.initializeIfAbsent(
                    stock.getStockCode(),
                    mockStockPriceProvider.getFallbackPrice(stock.getStockCode())));
    log.info("[DataInitializer] 초기 시세 삽입 완료");
  }

  private void initDailyCandles() {
    if (mockStockPriceProvider == null) {
      log.info("[DataInitializer] mock 비활성화 — daily candle 삽입 skip");
      return;
    }
    stockMasterRepository
        .findAll()
        .forEach(
            stock ->
                stockPriceHistoryService.initDailyCandles(
                    stock.getStockCode(),
                    mockStockPriceProvider.getFallbackPrice(stock.getStockCode())));
    log.info("[DataInitializer] daily candle 삽입 완료");
  }
}
