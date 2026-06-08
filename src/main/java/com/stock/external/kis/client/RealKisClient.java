package com.stock.external.kis.client;

import com.stock.external.kis.auth.KisTokenManager;
import com.stock.external.kis.config.KisProperties;
import com.stock.external.kis.dto.KisCurrentPriceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class RealKisClient implements KisClient {

  private static final String PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";

  private final KisProperties kisProperties;
  private final KisTokenManager kisTokenManager;
  private final RestTemplate restTemplate;

  @Override
  public KisCurrentPriceResponse getCurrentPrice(String stockCode) {
    String url =
        UriComponentsBuilder.fromHttpUrl(kisProperties.getBaseUrl() + PRICE_PATH)
            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
            .queryParam("FID_INPUT_ISCD", stockCode)
            .build()
            .toUriString();

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + kisTokenManager.getToken());
    headers.set("appkey", kisProperties.getAppKey());
    headers.set("appsecret", kisProperties.getAppSecret());
    headers.set("tr_id", "FHKST01010100");
    headers.set("custtype", "P");

    ResponseEntity<KisCurrentPriceResponse> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), KisCurrentPriceResponse.class);

    log.debug("[RealKisClient] 현재가 조회 stockCode={}", stockCode);
    return response.getBody();
  }
}
