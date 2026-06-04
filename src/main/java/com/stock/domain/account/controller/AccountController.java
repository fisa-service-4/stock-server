package com.stock.domain.account.controller;

import com.stock.domain.account.dto.request.AccountValidateRequest;
import com.stock.domain.account.dto.request.CashRequest;
import com.stock.domain.account.dto.response.AccountResponse;
import com.stock.domain.account.dto.response.AccountValidateResponse;
import com.stock.domain.account.dto.response.CashBalanceResponse;
import com.stock.domain.account.dto.response.CashResponse;
import com.stock.domain.account.service.AccountService;
import com.stock.domain.account.service.CashService;
import com.stock.global.constants.HeaderConstants;
import com.stock.global.response.ApiResponse;
import com.stock.global.response.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final CashService cashService;

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

  @Operation(summary = "계좌 유효성 검증", description = "기관 코드와 계좌번호로 계좌 존재 여부 및 사용 가능 상태를 검증합니다.")
  @PostMapping("/accounts/validate")
  public ResponseEntity<ApiResponse<AccountValidateResponse>> validateAccount(
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @RequestBody @Valid AccountValidateRequest request) {
    log.info(
        "[{}] 계좌 유효성 검증 요청 toBankCode={} toAccountNumber={}",
        MDC.get("traceId"),
        request.getToBankCode(),
        request.getToAccountNumber());
    AccountValidateResponse result = accountService.validateAccount(request);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }

  @Operation(summary = "예수금 입금 (Saga)", description = "transaction-server Saga step — 예수금 증가 처리.")
  @PostMapping("/accounts/cash/deposit")
  public ResponseEntity<ApiResponse<CashResponse>> deposit(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @RequestBody @Valid CashRequest request) {
    log.info(
        "[{}] [userId={}] 예수금 입금 요청 accountNumber={} amount={}",
        MDC.get("traceId"),
        userId,
        request.getAccountNumber(),
        request.getAmount());
    CashResponse result = cashService.deposit(userId, request);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }

  @Operation(
      summary = "예수금 출금 (Saga compensation)",
      description = "transaction-server Saga rollback — 예수금 차감 처리.")
  @PostMapping("/accounts/cash/withdraw")
  public ResponseEntity<ApiResponse<CashResponse>> withdraw(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @RequestBody @Valid CashRequest request) {
    log.info(
        "[{}] [userId={}] 예수금 출금 요청 accountNumber={} amount={}",
        MDC.get("traceId"),
        userId,
        request.getAccountNumber(),
        request.getAmount());
    CashResponse result = cashService.withdraw(userId, request);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
