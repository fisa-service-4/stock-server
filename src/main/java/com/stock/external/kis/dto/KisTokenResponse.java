package com.stock.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KisTokenResponse {

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private long expiresIn;

  @JsonProperty("access_token_token_expired")
  private String tokenExpired;
}
