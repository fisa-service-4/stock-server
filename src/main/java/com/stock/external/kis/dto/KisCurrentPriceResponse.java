package com.stock.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KisCurrentPriceResponse {

  @JsonProperty("rt_cd")
  private String rtCd;

  @JsonProperty("msg_cd")
  private String msgCd;

  @JsonProperty("msg1")
  private String msg1;

  private Output output;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Output {

    @JsonProperty("stck_prpr")
    private String stckPrpr;

    @JsonProperty("prdy_vrss")
    private String prdyVrss;

    @JsonProperty("prdy_ctrt")
    private String prdyCtrt;
  }
}
