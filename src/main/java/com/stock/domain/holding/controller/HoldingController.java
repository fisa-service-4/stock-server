package com.stock.domain.holding.controller;

import com.stock.domain.holding.dto.response.HoldingResponse;
import com.stock.domain.holding.dto.response.HoldingReturnResponse;
import com.stock.domain.holding.service.HoldingService;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Holding", description = "보유종목 API")
@RestController
@RequestMapping("/internal/v1/stock")
@RequiredArgsConstructor
public class HoldingController {

  private final HoldingService holdingService;

  @Operation(summary = "보유종목 조회", description = "계좌의 보유종목 목록을 실시간 평가금액과 함께 조회합니다.")
  @GetMapping("/accounts/{accountId}/holdings")
  public ResponseEntity<ApiResponse<List<HoldingResponse>>> getHoldings(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long accountId) {
    log.info("[{}] [userId={}] 보유종목 조회 accountId={}", MDC.get("traceId"), userId, accountId);
    List<HoldingResponse> result = holdingService.getHoldings(userId, accountId);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }

  @Operation(summary = "수익률 조회", description = "계좌의 실시간 수익률을 조회합니다.")
  @GetMapping("/accounts/{accountId}/returns")
  public ResponseEntity<ApiResponse<HoldingReturnResponse>> getReturns(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long accountId) {
    log.info("[{}] [userId={}] 수익률 조회 accountId={}", MDC.get("traceId"), userId, accountId);
    HoldingReturnResponse result = holdingService.getReturns(userId, accountId);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
