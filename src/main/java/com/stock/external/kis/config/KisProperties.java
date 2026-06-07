package com.stock.external.kis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kis")
public class KisProperties {

  private String appKey;
  private String appSecret;
  private String baseUrl = "https://openapivts.koreainvestment.com:29443";
  private long callDelayMs = 100;
}
