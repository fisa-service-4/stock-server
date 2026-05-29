package com.stock.domain.account.controller;

import com.stock.domain.account.dto.response.AccountResponse;
import com.stock.domain.account.dto.response.CashBalanceResponse;
import com.stock.domain.account.service.AccountService;
import com.stock.global.constants.HeaderConstants;
import com.stock.global.response.ApiResponse;
import com.stock.global.response.ContentWrapper;
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
@Tag(name = "Account", description = "증권 계좌 API")
@RestController
@RequestMapping("/internal/v1/stock")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @Operation(summary = "주문 가능 계좌 조회", description = "사용자의 증권 계좌 목록을 조회합니다.")
  @GetMapping("/accounts")
  public ResponseEntity<ApiResponse<ContentWrapper<AccountResponse>>> getAccounts(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId) {
    log.info("[{}] [userId={}] 계좌 조회 요청", MDC.get("traceId"), userId);
    List<AccountResponse> result = accountService.getAccounts(userId);
    return ResponseEntity.ok(ApiResponse.success(ContentWrapper.of(result), traceId));
  }

  @Operation(summary = "예수금 조회", description = "증권 계좌의 예수금 및 출금 가능 금액을 조회합니다.")
  @GetMapping("/accounts/{accountId}/cash-balance")
  public ResponseEntity<ApiResponse<CashBalanceResponse>> getCashBalance(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long accountId) {
    log.info("[{}] [userId={}] 예수금 조회 요청 accountId={}", MDC.get("traceId"), userId, accountId);
    CashBalanceResponse result = accountService.getCashBalance(userId, accountId);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
