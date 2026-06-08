package com.stock.external.kis.provider;

import java.math.BigDecimal;

public interface StockPriceProvider {
  BigDecimal getCurrentPrice(String stockCode);
}
