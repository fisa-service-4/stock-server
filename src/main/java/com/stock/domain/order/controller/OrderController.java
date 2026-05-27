package com.stock.domain.order.controller;

import com.stock.domain.order.dto.request.OrderCreateRequest;
import com.stock.domain.order.dto.response.OrderCancelResponse;
import com.stock.domain.order.dto.response.OrderCreateResponse;
import com.stock.domain.order.dto.response.OrderDetailResponse;
import com.stock.domain.order.dto.response.OrderListItemResponse;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.service.OrderQueryService;
import com.stock.domain.order.service.OrderService;
import com.stock.global.constants.HeaderConstants;
import com.stock.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/internal/v1/stock")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;
  private final OrderQueryService orderQueryService;

  @Operation(
      summary = "주문 생성",
      description = "매수/매도 주문을 생성합니다. MARKET은 즉시 체결, LIMIT은 조건 충족 시 체결됩니다.")
  @PostMapping("/accounts/{accountId}/orders")
  public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @RequestHeader(value = "Pin-Token", required = false) String pinToken,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable Long accountId,
      @Valid @RequestBody OrderCreateRequest request) {
    log.info(
        "[{}] [userId={}] 주문 생성 요청 accountId={} stockCode={} type={} method={}",
        MDC.get("traceId"),
        userId,
        accountId,
        request.getStockCode(),
        request.getOrderType(),
        request.getOrderMethod());
    OrderCreateResponse result =
        orderService.createOrder(userId, accountId, pinToken, idempotencyKey, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, traceId));
  }

  @Operation(summary = "주문 취소", description = "REQUESTED 상태의 주문을 취소합니다.")
  @PostMapping("/orders/{orderId}/cancel")
  public ResponseEntity<ApiResponse<OrderCancelResponse>> cancelOrder(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long orderId) {
    log.info("[{}] [userId={}] 주문 취소 요청 orderId={}", MDC.get("traceId"), userId, orderId);
    OrderCancelResponse result = orderService.cancelOrder(userId, orderId);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }

  @Operation(summary = "주문 목록 조회", description = "계좌의 주문 목록을 조회합니다. status/orderType으로 필터링 가능합니다.")
  @GetMapping("/accounts/{accountId}/orders")
  public ResponseEntity<ApiResponse<Page<OrderListItemResponse>>> getOrders(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long accountId,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) OrderType orderType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    log.info(
        "[{}] [userId={}] 주문 목록 조회 accountId={} status={} orderType={}",
        MDC.get("traceId"),
        userId,
        accountId,
        status,
        orderType);
    Page<OrderListItemResponse> result =
        orderQueryService.getOrders(userId, accountId, status, orderType, page, size);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }

  @Operation(summary = "주문 상세 조회", description = "주문 ID로 주문 상세 정보를 조회합니다.")
  @GetMapping("/orders/{orderId}")
  public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(
      @RequestHeader(value = HeaderConstants.USER_ID, required = false) Long userId,
      @RequestHeader(value = HeaderConstants.TRACE_ID, required = false) String traceId,
      @PathVariable Long orderId) {
    log.info("[{}] [userId={}] 주문 상세 조회 orderId={}", MDC.get("traceId"), userId, orderId);
    OrderDetailResponse result = orderQueryService.getOrderDetail(userId, orderId);
    return ResponseEntity.ok(ApiResponse.success(result, traceId));
  }
}
