package com.stock.external.kis.mapper;

import com.stock.external.kis.dto.KisCurrentPriceResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class KisMapper {

  public BigDecimal toCurrentPrice(KisCurrentPriceResponse response) {
    return new BigDecimal(response.getOutput().getStckPrpr());
  }

  public BigDecimal toChangeAmount(KisCurrentPriceResponse response) {
    return new BigDecimal(response.getOutput().getPrdyVrss());
  }

  public BigDecimal toChangeRate(KisCurrentPriceResponse response) {
    return new BigDecimal(response.getOutput().getPrdyCtrt());
  }
}
