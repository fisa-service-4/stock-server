package com.stock.domain.stock.service;

import com.stock.domain.stock.dto.response.StockChartResponse;
import com.stock.domain.stock.dto.response.StockChartResponse.CandleItem;
import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import com.stock.external.kis.dummy.generator.MockPriceGenerator;
import com.stock.external.kis.provider.StockPriceProvider;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceHistoryService {

  private final StockPriceHistoryRepository stockPriceHistoryRepository;
  private final StockMasterRepository stockMasterRepository;
  private final MockPriceGenerator mockPriceGenerator;

  @Value("${stock.mock.enabled:true}")
  private boolean mockEnabled;

  @Autowired(required = false)
  private StockPriceProvider stockPriceProvider;

  @Lazy @Autowired private StockPriceHistoryService self;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordTick(String stockCode, BigDecimal newClose) {
    if (newClose == null) {
      log.warn("[StockPriceHistoryService] 전달된 현재가가 null입니다. stockCode={}", stockCode);
      return;
    }
    BigDecimal prevClose =
        stockPriceHistoryRepository
            .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
            .map(StockPriceHistory::getClosePrice)
            .orElse(newClose)
            .setScale(0, RoundingMode.HALF_UP);
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

  public StockPriceResponse getCurrentPrice(String stockCode) {
    StockMaster master =
        stockMasterRepository
            .findById(stockCode)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 종목 없음 stockCode={}", MDC.get("traceId"), stockCode);
                  return new GlobalException(ErrorCode.STOCK_001);
                });

    // KIS 모드: GET /price 요청 시 실시간 조회 후 tick 저장
    if (!mockEnabled && stockPriceProvider != null) {
      try {
        BigDecimal realTimePrice = stockPriceProvider.getCurrentPrice(stockCode);
        self.recordTick(stockCode, realTimePrice);
      } catch (Exception e) {
        log.warn(
            "[{}] KIS 실시간 조회 실패, DB fallback 사용 stockCode={} error={}",
            MDC.get("traceId"),
            stockCode,
            e.getMessage());
      }
    }

    StockPriceHistory history =
        stockPriceHistoryRepository
            .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 현재가 데이터 없음 stockCode={}", MDC.get("traceId"), stockCode);
                  return new GlobalException(ErrorCode.STOCK_002);
                });

    BigDecimal yesterdayClose =
        stockPriceHistoryRepository
            .findTopByStockCodeAndTradedDateBeforeOrderByTradedDateDescCollectedAtDesc(
                stockCode, LocalDate.now())
            .map(StockPriceHistory::getClosePrice)
            .orElse(history.getClosePrice());

    log.info(
        "[{}] 현재가 조회 성공 stockCode={} currentPrice={} yesterdayClose={}",
        MDC.get("traceId"),
        stockCode,
        history.getClosePrice(),
        yesterdayClose);
    return StockPriceResponse.of(master, history, yesterdayClose);
  }

  public void initDailyCandles(String stockCode, BigDecimal fallbackPrice) {
    BigDecimal prevClose =
        stockPriceHistoryRepository
            .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
            .map(StockPriceHistory::getClosePrice)
            .orElse(fallbackPrice)
            .setScale(0, RoundingMode.HALF_UP);

    LocalDate today = LocalDate.now();

    for (int i = 60; i >= 1; i--) {
      LocalDate date = today.minusDays(i);
      DayOfWeek dow = date.getDayOfWeek();
      if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
        continue;
      }

      if (stockPriceHistoryRepository.existsByStockCodeAndTradedDate(stockCode, date)) {
        continue;
      }

      BigDecimal open = prevClose;
      BigDecimal close = mockPriceGenerator.generate(open);
      BigDecimal high =
          close
              .max(open)
              .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(Math.random() * 0.005)))
              .setScale(0, RoundingMode.HALF_UP);
      BigDecimal low =
          close
              .min(open)
              .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(Math.random() * 0.005)))
              .setScale(0, RoundingMode.HALF_UP)
              .max(BigDecimal.ONE);
      BigDecimal fluctuationRate =
          open.compareTo(BigDecimal.ZERO) != 0
              ? close
                  .subtract(open)
                  .divide(open, 4, RoundingMode.HALF_UP)
                  .multiply(new BigDecimal("100"))
                  .setScale(2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      long volume = ThreadLocalRandom.current().nextLong(100_000, 1_000_001);

      prevClose = close;

      stockPriceHistoryRepository.save(
          StockPriceHistory.builder()
              .stockCode(stockCode)
              .tradedDate(date)
              .openPrice(open)
              .highPrice(high)
              .lowPrice(low)
              .closePrice(close)
              .volume(volume)
              .fluctuationRate(fluctuationRate)
              .collectedAt(date.atTime(15, 30))
              .build());

      log.info(
          "[StockPriceHistoryService] daily candle 삽입 stockCode={} date={} close={}",
          stockCode,
          date,
          close);
    }
  }

  @Transactional(readOnly = true)
  public StockChartResponse getChart(
      String stockCode, LocalDate from, LocalDate to, String interval) {
    stockMasterRepository
        .findById(stockCode)
        .orElseThrow(
            () -> {
              log.warn("[{}] 차트 조회 종목 없음 stockCode={}", MDC.get("traceId"), stockCode);
              return new GlobalException(ErrorCode.STOCK_001);
            });

    List<StockPriceHistory> histories =
        stockPriceHistoryRepository.findByStockCodeAndTradedDateBetweenOrderByTradedDateAsc(
            stockCode, from, to);

    if (histories.isEmpty()) {
      log.warn(
          "[{}] 차트 데이터 없음 stockCode={} from={} to={}", MDC.get("traceId"), stockCode, from, to);
      throw new GlobalException(ErrorCode.STOCK_003);
    }

    List<CandleItem> content =
        switch (interval.toUpperCase()) {
          case "DAILY" -> buildDaily(histories);
          case "WEEKLY" -> buildWeekly(histories);
          case "MONTHLY" -> buildMonthly(histories);
          default -> throw new GlobalException(ErrorCode.VALID_001);
        };

    log.info(
        "[{}] 차트 조회 성공 stockCode={} interval={} count={}",
        MDC.get("traceId"),
        stockCode,
        interval,
        content.size());

    return StockChartResponse.builder().content(content).build();
  }

  private List<CandleItem> buildDaily(List<StockPriceHistory> histories) {
    return histories.stream()
        .collect(
            Collectors.groupingBy(
                StockPriceHistory::getTradedDate, TreeMap::new, Collectors.toList()))
        .values()
        .stream()
        .map(group -> aggregateCandle(group, group.get(0).getTradedDate()))
        .toList();
  }

  private List<CandleItem> buildWeekly(List<StockPriceHistory> histories) {
    return histories.stream()
        .collect(
            Collectors.groupingBy(
                h -> {
                  int weekYear = h.getTradedDate().get(IsoFields.WEEK_BASED_YEAR);
                  int weekNum = h.getTradedDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                  return weekYear * 100 + weekNum;
                },
                TreeMap::new,
                Collectors.toList()))
        .values()
        .stream()
        .map(group -> aggregateCandle(group, group.get(0).getTradedDate()))
        .toList();
  }

  private List<CandleItem> buildMonthly(List<StockPriceHistory> histories) {
    return histories.stream()
        .collect(
            Collectors.groupingBy(
                h -> YearMonth.from(h.getTradedDate()), TreeMap::new, Collectors.toList()))
        .values()
        .stream()
        .map(group -> aggregateCandle(group, group.get(0).getTradedDate()))
        .toList();
  }

  private CandleItem aggregateCandle(List<StockPriceHistory> group, LocalDate representativeDate) {
    group.sort(Comparator.comparing(StockPriceHistory::getCollectedAt));
    BigDecimal open = group.get(0).getOpenPrice();
    BigDecimal close = group.get(group.size() - 1).getClosePrice();
    BigDecimal high =
        group.stream()
            .map(StockPriceHistory::getHighPrice)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
    BigDecimal low =
        group.stream()
            .map(StockPriceHistory::getLowPrice)
            .min(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
    long volume = group.stream().mapToLong(StockPriceHistory::getVolume).sum();

    return CandleItem.builder()
        .date(representativeDate)
        .open(open.setScale(0, RoundingMode.HALF_UP).longValue())
        .high(high.setScale(0, RoundingMode.HALF_UP).longValue())
        .low(low.setScale(0, RoundingMode.HALF_UP).longValue())
        .close(close.setScale(0, RoundingMode.HALF_UP).longValue())
        .volume(volume)
        .build();
  }
}
