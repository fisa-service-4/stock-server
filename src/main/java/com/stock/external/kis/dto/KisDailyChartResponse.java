package com.stock.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KisDailyChartResponse {

  @JsonProperty("rt_cd")
  private String rtCd;

  @JsonProperty("msg_cd")
  private String msgCd;

  @JsonProperty("msg1")
  private String msg1;

  @JsonProperty("output2")
  private List<DailyItem> output2;

  @Getter
  @NoArgsConstructor
  public static class DailyItem {

    @JsonProperty("stck_bsop_date")
    private String stckBsopDate;

    @JsonProperty("stck_oprc")
    private String stckOprc;

    @JsonProperty("stck_hgpr")
    private String stckHgpr;

    @JsonProperty("stck_lwpr")
    private String stckLwpr;

    @JsonProperty("stck_clpr")
    private String stckClpr;

    @JsonProperty("acml_vol")
    private String acmlVol;

    @JsonProperty("prdy_ctrt")
    private String prdyCtrt;
  }
}
