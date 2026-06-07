package com.stock.external.kis.auth;

// import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.external.kis.config.KisProperties;
import com.stock.external.kis.dto.KisTokenRequest;
import com.stock.external.kis.dto.KisTokenResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "stock.mock", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class KisTokenManager {

  private final KisProperties kisProperties;
  private final RestTemplate restTemplate;

  private volatile String cachedToken;
  private volatile LocalDateTime expireAt;

  public synchronized String getToken() {
    if (cachedToken == null
        || expireAt == null
        || LocalDateTime.now().isAfter(expireAt.minusHours(1))) {
      issueToken();
    }
    return cachedToken;
  }

  private void issueToken() {
    String url = kisProperties.getBaseUrl() + "/oauth2/tokenP";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    KisTokenRequest body =
        new KisTokenRequest(
            "client_credentials", kisProperties.getAppKey(), kisProperties.getAppSecret());

    // 디버깅용 로그
    //    try {
    //      ObjectMapper mapper = new ObjectMapper();
    //      log.info("KIS TOKEN REQUEST={}", mapper.writeValueAsString(body));
    //    } catch (Exception e) {
    //      log.error("JSON 직렬화 실패", e);
    //    }
    //
    //    log.info("grantType={}", body.getGrantType());
    //    log.info("appKey={}", body.getAppKey());
    //    log.info("appSecret exists={}", body.getAppSecret() != null);

    HttpEntity<KisTokenRequest> request = new HttpEntity<>(body, headers);

    log.info("[KisTokenManager] KIS 토큰 발급 요청");

    KisTokenResponse response = restTemplate.postForObject(url, request, KisTokenResponse.class);

    if (response == null) {
      throw new IllegalStateException("KIS 토큰 응답이 비어 있습니다.");
    }

    cachedToken = response.getAccessToken();
    expireAt = LocalDateTime.now().plusSeconds(response.getExpiresIn());

    log.info("[KisTokenManager] 토큰 발급 완료 expireAt={}", expireAt);
  }
}
