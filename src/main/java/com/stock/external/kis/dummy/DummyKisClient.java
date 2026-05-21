package com.stock.external.kis.dummy;

import com.stock.external.kis.client.KisClient;
import com.stock.external.kis.dto.KisCurrentPriceResponse;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DummyKisClient implements KisClient {

  private static final Map<String, Long> BASE_PRICES =
      Map.of(
          "005930", 80500L,
          "000660", 185000L,
          "035420", 195000L,
          "035720", 43000L);

  @Override
  public KisCurrentPriceResponse getCurrentPrice(String stockCode) {
    long base = BASE_PRICES.getOrDefault(stockCode, 50000L);
    double variation = 1 + (Math.random() * 0.06 - 0.03);
    long current = Math.round(base * variation);
    long change = current - base;
    double changeRate = (double) change / base * 100;

    return KisCurrentPriceResponse.builder()
        .rtCd("0")
        .msgCd("MCA00000")
        .msg1("정상처리 되었습니다.")
        .output(
            KisCurrentPriceResponse.Output.builder()
                .stckPrpr(String.valueOf(current))
                .prdyVrss(String.valueOf(change))
                .prdyCtrt(String.format("%.2f", changeRate))
                .build())
        .build();
  }
}
