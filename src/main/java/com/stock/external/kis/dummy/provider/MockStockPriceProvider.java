package com.stock.external.kis.dummy.provider;

import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import com.stock.external.kis.dummy.generator.MockPriceGenerator;
import com.stock.external.kis.provider.StockPriceProvider;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MockStockPriceProvider implements StockPriceProvider {

  private static final Map<String, BigDecimal> FALLBACK_PRICES =
      Map.ofEntries(
          Map.entry("005930", new BigDecimal("73000")),
          Map.entry("000660", new BigDecimal("180000")),
          Map.entry("373220", new BigDecimal("320000")),
          Map.entry("207940", new BigDecimal("830000")),
          Map.entry("005380", new BigDecimal("230000")),
          Map.entry("000270", new BigDecimal("110000")),
          Map.entry("035420", new BigDecimal("220000")),
          Map.entry("068270", new BigDecimal("190000")),
          Map.entry("006400", new BigDecimal("190000")),
          Map.entry("066570", new BigDecimal("83000")),
          Map.entry("051910", new BigDecimal("230000")),
          Map.entry("096770", new BigDecimal("120000")),
          Map.entry("005490", new BigDecimal("350000")),
          Map.entry("003670", new BigDecimal("95000")),
          Map.entry("010130", new BigDecimal("680000")),
          Map.entry("012330", new BigDecimal("220000")),
          Map.entry("028260", new BigDecimal("130000")),
          Map.entry("009150", new BigDecimal("120000")),
          Map.entry("018260", new BigDecimal("170000")),
          Map.entry("015760", new BigDecimal("24000")),
          Map.entry("010950", new BigDecimal("78000")),
          Map.entry("033780", new BigDecimal("98000")),
          Map.entry("030200", new BigDecimal("44000")),
          Map.entry("017670", new BigDecimal("55000")),
          Map.entry("011200", new BigDecimal("18000")),
          Map.entry("003490", new BigDecimal("25000")),
          Map.entry("097950", new BigDecimal("200000")),
          Map.entry("000120", new BigDecimal("120000")),
          Map.entry("271560", new BigDecimal("120000")),
          Map.entry("090430", new BigDecimal("120000")),
          Map.entry("000100", new BigDecimal("100000")),
          Map.entry("161390", new BigDecimal("60000")),
          Map.entry("329180", new BigDecimal("200000")),
          Map.entry("009540", new BigDecimal("200000")),
          Map.entry("267250", new BigDecimal("80000")),
          Map.entry("251270", new BigDecimal("60000")),
          Map.entry("259960", new BigDecimal("290000")),
          Map.entry("036570", new BigDecimal("200000")),
          Map.entry("105560", new BigDecimal("80000")),
          Map.entry("055550", new BigDecimal("55000")),
          Map.entry("086790", new BigDecimal("65000")),
          Map.entry("316140", new BigDecimal("18000")),
          Map.entry("032830", new BigDecimal("100000")),
          Map.entry("000810", new BigDecimal("290000")),
          Map.entry("005830", new BigDecimal("90000")),
          Map.entry("006800", new BigDecimal("12000")),
          Map.entry("071050", new BigDecimal("90000")),
          Map.entry("039490", new BigDecimal("120000")),
          Map.entry("138040", new BigDecimal("100000")),
          Map.entry("323410", new BigDecimal("24000")));

  private final MockPriceGenerator mockPriceGenerator;
  private final StockPriceHistoryRepository stockPriceHistoryRepository;

  @Override
  public BigDecimal getCurrentPrice(String stockCode) {
    BigDecimal prevClose =
        stockPriceHistoryRepository
            .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
            .map(StockPriceHistory::getClosePrice)
            .orElse(getFallbackPrice(stockCode));
    BigDecimal next = mockPriceGenerator.generate(prevClose);
    log.debug(
        "[MockStockPriceProvider] 시세 생성 stockCode={} prev={} next={}", stockCode, prevClose, next);
    return next;
  }

  public BigDecimal getFallbackPrice(String stockCode) {
    return FALLBACK_PRICES.getOrDefault(stockCode, new BigDecimal("50000"));
  }
}
