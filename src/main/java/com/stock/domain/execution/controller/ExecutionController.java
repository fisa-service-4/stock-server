package com.stock.domain.execution.controller;

import com.stock.domain.execution.dto.response.ExecutionResponse;
import com.stock.domain.execution.service.ExecutionService;
import com.stock.global.constants.HeaderConstants;
import com.stock.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Execution", description = "체결 API")
@RestController
@RequestMapping("/internal/v1/stock")
@RequiredArgsConstructor
public class ExecutionController {

  private final ExecutionService executionService;

  @Operation(
      summary = "체결 내역 조회",
      description = "계좌의 체결 내역을 조회합니다. stockCode/fromDate/toDate로 필터링 가능합니다.")
  @GetMapping("/accounts/{accountId}/executions")
  public ResponseEntity<ApiResponse<Page<ExecutionResponse>>> getExecutions(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long accountId,
      @RequestParam(required = false) String stockCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    log.info(
        "[{}] [userId={}] 체결 조회 accountId={} stockCode={} from={} to={}",
        MDC.get("traceId"),
        userId,
        accountId,
        stockCode,
        fromDate,
        toDate);
    Page<ExecutionResponse> result =
        executionService.getExecutions(userId, accountId, stockCode, fromDate, toDate, page, size);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
