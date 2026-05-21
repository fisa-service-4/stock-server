package com.stock.external.kis.dummy.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class MockPriceGenerator {

  public BigDecimal generate(BigDecimal lastPrice) {
    double variationRate = Math.random() * 0.06 - 0.03;

    BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(variationRate));

    BigDecimal generatedPrice = lastPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

    return generatedPrice.max(BigDecimal.ONE);
  }
}
