package com.stock.domain.stock.runner;

import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import com.stock.external.kis.client.KisClient;
import com.stock.external.kis.config.KisProperties;
import com.stock.external.kis.dto.KisDailyChartResponse;
import com.stock.external.kis.dto.KisDailyChartResponse.DailyItem;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("seed")
@Component
@RequiredArgsConstructor
public class StockPriceHistorySeedRunner implements ApplicationRunner {

  private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final int FROM_DAYS_BACK = 90;

  private final StockMasterRepository stockMasterRepository;
  private final StockPriceHistoryRepository stockPriceHistoryRepository;
  private final KisClient kisClient;
  private final KisProperties kisProperties;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    List<String> stockCodes =
        stockMasterRepository.findAll().stream().map(s -> s.getStockCode()).toList();

    LocalDate toDate = getLastBusinessDay();
    LocalDate fromDate = toDate.minusDays(FROM_DAYS_BACK);

    log.info(
        "[SeedRunner] KIS 일봉 시드 시작 stockCount={} from={} to={}",
        stockCodes.size(),
        fromDate,
        toDate);

    int successCount = 0;
    int skipCount = 0;
    int failCount = 0;

    for (String stockCode : stockCodes) {
      if (Thread.currentThread().isInterrupted()) {
        log.warn(
            "[SeedRunner] 인터럽트 감지 — 시드 중단 (processed={}/{})",
            successCount + skipCount + failCount,
            stockCodes.size());
        break;
      }
      try {
        int saved = seedStock(stockCode, fromDate, toDate);
        if (saved == 0) {
          skipCount++;
        } else {
          successCount++;
        }
        log.info("[SeedRunner] {} — {} 건 저장", stockCode, saved);
      } catch (Exception e) {
        failCount++;
        log.warn("[SeedRunner] {} 실패: {}", stockCode, e.getMessage());
      }

      sleepForRateLimit();
    }

    log.info("[SeedRunner] 완료 success={} skip={} fail={}", successCount, skipCount, failCount);
  }

  private int seedStock(String stockCode, LocalDate fromDate, LocalDate toDate) {
    KisDailyChartResponse response = kisClient.getDailyChart(stockCode, fromDate, toDate);

    if (response == null || !"0".equals(response.getRtCd())) {
      String rtCd = response != null ? response.getRtCd() : "null";
      String msgCd = response != null ? response.getMsgCd() : "null";
      String msg1 = response != null ? response.getMsg1() : "null";
      log.warn(
          "[SeedRunner] KIS 응답 오류 stockCode={} rt_cd={} msg_cd={} msg1={}",
          stockCode,
          rtCd,
          msgCd,
          msg1);
      return 0;
    }

    List<DailyItem> items = response.getOutput2();
    if (items == null || items.isEmpty()) {
      log.info("[SeedRunner] 응답 데이터 없음 stockCode={}", stockCode);
      return 0;
    }

    List<StockPriceHistory> toSave = new ArrayList<>();
    for (DailyItem item : items) {
      LocalDate tradedDate = LocalDate.parse(item.getStckBsopDate(), KIS_DATE);

      if (stockPriceHistoryRepository.existsByStockCodeAndTradedDate(stockCode, tradedDate)) {
        continue;
      }

      toSave.add(
          StockPriceHistory.builder()
              .stockCode(stockCode)
              .tradedDate(tradedDate)
              .openPrice(parseSafe(item.getStckOprc()))
              .highPrice(parseSafe(item.getStckHgpr()))
              .lowPrice(parseSafe(item.getStckLwpr()))
              .closePrice(parseSafe(item.getStckClpr()))
              .volume(parseLongSafe(item.getAcmlVol()))
              .fluctuationRate(parseSafe(item.getPrdyCtrt()))
              .collectedAt(tradedDate.atTime(LocalTime.of(15, 30)))
              .build());
    }

    if (!toSave.isEmpty()) {
      stockPriceHistoryRepository.saveAll(toSave);
    }
    return toSave.size();
  }

  private LocalDate getLastBusinessDay() {
    LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      date = date.minusDays(1);
    }
    return date;
  }

  private void sleepForRateLimit() {
    try {
      Thread.sleep(kisProperties.getCallDelayMs());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("[SeedRunner] 중단됨 (interrupted)");
    }
  }

  private BigDecimal parseSafe(String value) {
    if (value == null || value.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(value.replace(",", ""));
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  private Long parseLongSafe(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(value.replace(",", ""));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
