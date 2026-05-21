package com.stock.domain.stock.controller;

import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.dto.response.StockSearchResponse;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.domain.stock.service.StockService;
import com.stock.global.constants.HeaderConstants;
import com.stock.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Stock", description = "종목 API")
@RestController
@RequestMapping("/internal/v1/stocks")
@RequiredArgsConstructor
public class StockController {

  private final StockService stockService;
  private final StockPriceHistoryService stockPriceHistoryService;

  @Operation(summary = "종목 검색", description = "종목명 또는 종목코드로 검색합니다.")
  @GetMapping("/search")
  public ResponseEntity<ApiResponse<List<StockSearchResponse>>> search(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @RequestParam String keyword) {
    log.info("[{}] [userId={}] 종목 검색 keyword={}", MDC.get("traceId"), userId, keyword);
    List<StockSearchResponse> result = stockService.search(keyword);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }

  @Operation(summary = "종목 현재가 조회", description = "종목 코드 기준 최신 시세 데이터를 반환합니다.")
  @GetMapping("/{stockCode}/price")
  public ResponseEntity<ApiResponse<StockPriceResponse>> getPrice(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable String stockCode) {
    log.info("[{}] [userId={}] 현재가 조회 stockCode={}", MDC.get("traceId"), userId, stockCode);
    StockPriceResponse result = stockPriceHistoryService.getCurrentPrice(stockCode);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
