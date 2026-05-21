package com.stock.external.kis.client;

import com.stock.external.kis.dto.KisCurrentPriceResponse;

public interface KisClient {

  KisCurrentPriceResponse getCurrentPrice(String stockCode);
}
