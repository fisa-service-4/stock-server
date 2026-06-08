package com.stock.external.kis.client;

import com.stock.external.kis.auth.KisTokenManager;
import com.stock.external.kis.config.KisProperties;
import com.stock.external.kis.dto.KisCurrentPriceResponse;
import com.stock.external.kis.dto.KisDailyChartResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
  private static final String DAILY_CHART_PATH =
      "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
  private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

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

    HttpHeaders headers = buildHeaders("FHKST01010100");

    ResponseEntity<KisCurrentPriceResponse> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), KisCurrentPriceResponse.class);

    log.debug("[RealKisClient] 현재가 조회 stockCode={}", stockCode);
    return response.getBody();
  }

  @Override
  public KisDailyChartResponse getDailyChart(
      String stockCode, LocalDate fromDate, LocalDate toDate) {
    String url =
        UriComponentsBuilder.fromHttpUrl(kisProperties.getBaseUrl() + DAILY_CHART_PATH)
            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
            .queryParam("FID_INPUT_ISCD", stockCode)
            .queryParam("FID_INPUT_DATE_1", fromDate.format(KIS_DATE))
            .queryParam("FID_INPUT_DATE_2", toDate.format(KIS_DATE))
            .queryParam("FID_PERIOD_DIV_CODE", "D")
            .queryParam("FID_ORG_ADJ_PRC", "0")
            .build()
            .toUriString();

    HttpHeaders headers = buildHeaders("FHKST03010100");

    ResponseEntity<KisDailyChartResponse> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), KisDailyChartResponse.class);

    log.debug("[RealKisClient] 일봉 조회 stockCode={} from={} to={}", stockCode, fromDate, toDate);
    return response.getBody();
  }

  private HttpHeaders buildHeaders(String trId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + kisTokenManager.getToken());
    headers.set("appkey", kisProperties.getAppKey());
    headers.set("appsecret", kisProperties.getAppSecret());
    headers.set("tr_id", trId);
    headers.set("custtype", "P");
    return headers;
  }
}
