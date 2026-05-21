package com.stock.domain.stock.controller;

import com.stock.domain.stock.dto.response.StockSearchResponse;
import com.stock.domain.stock.service.StockService;
import com.stock.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stock", description = "종목 API")
@RestController
@RequestMapping("/internal/v1/stocks")
@RequiredArgsConstructor
public class StockController {

  private final StockService stockService;

  @Operation(summary = "종목 검색", description = "종목명 또는 종목코드로 검색합니다.")
  @GetMapping("/search")
  public ResponseEntity<ApiResponse<List<StockSearchResponse>>> search(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestParam String keyword) {
    List<StockSearchResponse> result = stockService.search(keyword);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
