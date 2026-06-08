package com.stock.external.kis.client;

import com.stock.external.kis.dto.KisCurrentPriceResponse;
import com.stock.external.kis.dto.KisDailyChartResponse;
import java.time.LocalDate;

public interface KisClient {

  KisCurrentPriceResponse getCurrentPrice(String stockCode);

  KisDailyChartResponse getDailyChart(String stockCode, LocalDate fromDate, LocalDate toDate);
}
