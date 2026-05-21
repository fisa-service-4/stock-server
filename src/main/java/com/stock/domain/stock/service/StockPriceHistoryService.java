package com.stock.domain.stock.service;

import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceHistoryService {

  private final StockPriceHistoryRepository stockPriceHistoryRepository;

  @Transactional
  public void recordTick(String stockCode, BigDecimal prevClose, BigDecimal newClose) {
    BigDecimal high = newClose.max(prevClose);
    BigDecimal low = newClose.min(prevClose);
    BigDecimal fluctuationRate =
        prevClose.compareTo(BigDecimal.ZERO) != 0
            ? newClose
                .subtract(prevClose)
                .divide(prevClose, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    long volume = ThreadLocalRandom.current().nextLong(1000, 50001);

    stockPriceHistoryRepository.save(
        StockPriceHistory.builder()
            .stockCode(stockCode)
            .tradedDate(LocalDate.now())
            .openPrice(prevClose)
            .highPrice(high)
            .lowPrice(low)
            .closePrice(newClose)
            .volume(volume)
            .fluctuationRate(fluctuationRate)
            .collectedAt(LocalDateTime.now())
            .build());

    log.info(
        "[StockPriceHistoryService] 시세 tick 저장 stockCode={} close={} fluctuationRate={}",
        stockCode,
        newClose,
        fluctuationRate);
  }

  @Transactional
  public void initializeIfAbsent(String stockCode, BigDecimal initialPrice) {
    if (stockPriceHistoryRepository
        .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
        .isPresent()) {
      log.info(
          "[StockPriceHistoryService] 초기 시세 이미 존재 skip stockCode={}", stockCode);
      return;
    }

    stockPriceHistoryRepository.save(
        StockPriceHistory.builder()
            .stockCode(stockCode)
            .tradedDate(LocalDate.now())
            .openPrice(initialPrice)
            .highPrice(initialPrice)
            .lowPrice(initialPrice)
            .closePrice(initialPrice)
            .volume(0L)
            .fluctuationRate(BigDecimal.ZERO)
            .collectedAt(LocalDateTime.now())
            .build());

    log.info(
        "[StockPriceHistoryService] 초기 시세 저장 stockCode={} price={}", stockCode, initialPrice);
  }
}
