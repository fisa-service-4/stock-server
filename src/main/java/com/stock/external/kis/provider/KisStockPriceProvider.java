package com.stock.external.kis.provider;

import com.stock.external.kis.client.KisClient;
import com.stock.external.kis.dto.KisCurrentPriceResponse;
import com.stock.external.kis.mapper.KisMapper;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class KisStockPriceProvider implements StockPriceProvider {

  private final KisClient kisClient;
  private final KisMapper kisMapper;

  @Override
  public BigDecimal getCurrentPrice(String stockCode) {
    KisCurrentPriceResponse response = kisClient.getCurrentPrice(stockCode);

    if (response == null || response.getOutput() == null || !"0".equals(response.getRtCd())) {
      String msgCd = response != null ? response.getMsgCd() : "null";
      String msg = response != null ? response.getMsg1() : "null";
      log.warn(
          "[KisStockPriceProvider] rt_cd 오류 또는 응답 바디 누락 stockCode={} msgCd={} msg={}",
          stockCode,
          msgCd,
          msg);
      throw new GlobalException(ErrorCode.STOCK_002);
    }

    return kisMapper.toCurrentPrice(response);
  }
}
