package com.stock.domain.stock.service;

import com.stock.domain.stock.dto.response.StockChartResponse;
import com.stock.domain.stock.dto.response.StockChartResponse.CandleItem;
import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceHistoryService {

  private final StockPriceHistoryRepository stockPriceHistoryRepository;
  private final StockMasterRepository stockMasterRepository;

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
      log.info("[StockPriceHistoryService] 초기 시세 이미 존재 skip stockCode={}", stockCode);
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

    log.info("[StockPriceHistoryService] 초기 시세 저장 stockCode={} price={}", stockCode, initialPrice);
  }

  @Transactional(readOnly = true)
  public StockPriceResponse getCurrentPrice(String stockCode) {
    StockMaster master =
        stockMasterRepository
            .findById(stockCode)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 종목 없음 stockCode={}", MDC.get("traceId"), stockCode);
                  return new GlobalException(ErrorCode.STOCK_001);
                });

    StockPriceHistory history =
        stockPriceHistoryRepository
            .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 현재가 데이터 없음 stockCode={}", MDC.get("traceId"), stockCode);
                  return new GlobalException(ErrorCode.STOCK_002);
                });

    log.info(
        "[{}] 현재가 조회 성공 stockCode={} currentPrice={}",
        MDC.get("traceId"),
        stockCode,
        history.getClosePrice());
    return StockPriceResponse.of(master, history);
  }

  @Transactional(readOnly = true)
  public StockChartResponse getChart(String stockCode, LocalDateTime from, LocalDateTime to) {
    stockMasterRepository
        .findById(stockCode)
        .orElseThrow(
            () -> {
              log.warn("[{}] 차트 조회 종목 없음 stockCode={}", MDC.get("traceId"), stockCode);
              return new GlobalException(ErrorCode.STOCK_001);
            });

    List<StockPriceHistory> histories =
        stockPriceHistoryRepository.findByStockCodeAndCollectedAtBetweenOrderByCollectedAtAsc(
            stockCode, from, to);

    if (histories.isEmpty()) {
      log.warn(
          "[{}] 차트 데이터 없음 stockCode={} from={} to={}", MDC.get("traceId"), stockCode, from, to);
      throw new GlobalException(ErrorCode.STOCK_003);
    }

    List<CandleItem> content = histories.stream().map(CandleItem::from).toList();

    log.info("[{}] 차트 조회 성공 stockCode={} count={}", MDC.get("traceId"), stockCode, content.size());

    return StockChartResponse.builder().content(content).build();
  }
}
