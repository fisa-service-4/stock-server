package com.stock.external.kis.dummy.provider;

import com.stock.external.kis.dummy.generator.MockPriceGenerator;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MockStockPriceProvider {

  private static final Map<String, BigDecimal> FALLBACK_PRICES =
      Map.of(
          "005930", new BigDecimal("70000"),
          "000660", new BigDecimal("210000"),
          "035420", new BigDecimal("190000"),
          "035720", new BigDecimal("42000"));

  private final MockPriceGenerator mockPriceGenerator;

  public BigDecimal getNextPrice(String stockCode, BigDecimal lastPrice) {
    BigDecimal base =
        lastPrice != null
            ? lastPrice
            : FALLBACK_PRICES.getOrDefault(stockCode, new BigDecimal("50000"));
    return mockPriceGenerator.generate(base);
  }

  public BigDecimal getFallbackPrice(String stockCode) {
    return FALLBACK_PRICES.getOrDefault(stockCode, new BigDecimal("50000"));
  }
}
