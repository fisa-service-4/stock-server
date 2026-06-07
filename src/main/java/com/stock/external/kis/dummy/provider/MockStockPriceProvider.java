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
      Map.of(
          "005930", new BigDecimal("355000"),
          "000660", new BigDecimal("2378000"),
          "035420", new BigDecimal("283500"),
          "035720", new BigDecimal("42950"));

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
