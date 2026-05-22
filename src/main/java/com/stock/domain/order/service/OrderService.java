package com.stock.domain.order.service;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.order.dto.request.OrderRequest;
import com.stock.domain.order.dto.response.OrderCreateResponse;
import com.stock.domain.order.dto.response.OrderDetailResponse;
import com.stock.domain.order.dto.response.OrderListItemResponse;
import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.entity.enums.OrderedBy;
import com.stock.domain.order.repository.StockOrderRepository;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import com.stock.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

  private final StockOrderRepository stockOrderRepository;
  private final StockMasterRepository stockMasterRepository;
  private final StockPriceHistoryRepository stockPriceHistoryRepository;
  private final StockHoldingRepository stockHoldingRepository;
  private final AccountValidator accountValidator;

  @Transactional
  public OrderCreateResponse createOrder(
      Long userId, Long accountId, String pinToken, String idempotencyKey, OrderRequest request) {

    validatePinToken(pinToken);

    if (StringUtils.hasText(idempotencyKey)) {
      return stockOrderRepository
          .findByIdempotencyKey(idempotencyKey)
          .map(
              existing -> {
                log.info("[{}] 중복 주문 요청 반환 idempotencyKey={}", MDC.get("traceId"), idempotencyKey);
                return OrderCreateResponse.from(existing);
              })
          .orElseGet(() -> saveNewOrder(userId, accountId, idempotencyKey, request));
    }

    return saveNewOrder(userId, accountId, idempotencyKey, request);
  }

  @Transactional(readOnly = true)
  public PageResponse<OrderListItemResponse> getOrders(
      Long userId, Long accountId, OrderStatus status, OrderType orderType, Pageable pageable) {

    accountValidator.validateOwner(userId, accountId);

    Page<StockOrder> orders = findOrdersWithFilter(accountId, status, orderType, pageable);

    Set<String> stockCodes = orders.map(StockOrder::getStockCode).toSet();
    Map<String, String> stockNameMap =
        stockMasterRepository.findAllById(stockCodes).stream()
            .collect(Collectors.toMap(StockMaster::getStockCode, StockMaster::getStockName));

    Page<OrderListItemResponse> result =
        orders.map(
            order ->
                OrderListItemResponse.from(
                    order, stockNameMap.getOrDefault(order.getStockCode(), "")));

    log.info(
        "[{}] [userId={}] 주문 목록 조회 accountId={} total={}",
        MDC.get("traceId"),
        userId,
        accountId,
        result.getTotalElements());

    return PageResponse.from(result);
  }

  @Transactional(readOnly = true)
  public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
    StockOrder order =
        stockOrderRepository
            .findById(orderId)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 주문 없음 orderId={}", MDC.get("traceId"), orderId);
                  return new GlobalException(ErrorCode.ORDER_003);
                });

    accountValidator.validateOwner(userId, order.getSecuritiesAccountId());

    String stockName =
        stockMasterRepository
            .findById(order.getStockCode())
            .map(StockMaster::getStockName)
            .orElse("");

    log.info("[{}] [userId={}] 주문 상세 조회 orderId={}", MDC.get("traceId"), userId, orderId);
    return OrderDetailResponse.from(order, stockName);
  }

  private OrderCreateResponse saveNewOrder(
      Long userId, Long accountId, String idempotencyKey, OrderRequest request) {

    SecuritiesAccount account = accountValidator.validateOwner(userId, accountId);

    StockMaster stock =
        stockMasterRepository
            .findById(request.getStockCode())
            .orElseThrow(
                () -> {
                  log.warn("[{}] 종목 없음 stockCode={}", MDC.get("traceId"), request.getStockCode());
                  return new GlobalException(ErrorCode.STOCK_001);
                });

    validateLimitPrice(request);
    validateOrderCondition(account, request);

    StockOrder order =
        StockOrder.builder()
            .securitiesAccountId(accountId)
            .stockCode(stock.getStockCode())
            .orderType(request.getOrderType())
            .orderMethod(request.getOrderMethod())
            .orderPrice(request.getPrice())
            .orderQuantity(request.getQuantity())
            .orderedBy(OrderedBy.USER)
            .orderedAt(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .build();

    StockOrder saved = stockOrderRepository.save(order);

    log.info(
        "[{}] [userId={}] 주문 생성 orderId={} stockCode={} type={} method={} qty={}",
        MDC.get("traceId"),
        userId,
        saved.getStockOrderId(),
        saved.getStockCode(),
        saved.getOrderType(),
        saved.getOrderMethod(),
        saved.getOrderQuantity());

    return OrderCreateResponse.from(saved);
  }

  private void validatePinToken(String pinToken) {
    if (!StringUtils.hasText(pinToken)) {
      log.warn("[{}] Pin-Token 누락", MDC.get("traceId"));
      throw new GlobalException(ErrorCode.VALID_002);
    }
  }

  private void validateLimitPrice(OrderRequest request) {
    if (request.getOrderMethod() == OrderMethod.LIMIT && request.getPrice() == null) {
      log.warn("[{}] LIMIT 주문 price 누락 stockCode={}", MDC.get("traceId"), request.getStockCode());
      throw new GlobalException(ErrorCode.VALID_002);
    }
  }

  private void validateOrderCondition(SecuritiesAccount account, OrderRequest request) {
    if (request.getOrderType() == OrderType.BUY) {
      validateBuyCondition(account, request);
    } else {
      validateSellCondition(account.getSecuritiesAccountId(), request);
    }
  }

  private void validateBuyCondition(SecuritiesAccount account, OrderRequest request) {
    BigDecimal price =
        request.getOrderMethod() == OrderMethod.LIMIT
            ? request.getPrice()
            : getCurrentPrice(request.getStockCode());

    BigDecimal required = price.multiply(BigDecimal.valueOf(request.getQuantity()));

    if (account.getCashBalance().compareTo(required) < 0) {
      log.warn(
          "[{}] 주문 가능 금액 부족 balance={} required={}",
          MDC.get("traceId"),
          account.getCashBalance(),
          required);
      throw new GlobalException(ErrorCode.ORDER_001);
    }
  }

  private void validateSellCondition(Long accountId, OrderRequest request) {
    StockHolding holding =
        stockHoldingRepository
            .findBySecuritiesAccountIdAndStockCode(accountId, request.getStockCode())
            .orElseThrow(
                () -> {
                  log.warn(
                      "[{}] 보유 종목 없음 stockCode={}", MDC.get("traceId"), request.getStockCode());
                  return new GlobalException(ErrorCode.ORDER_002);
                });

    if (holding.getHoldingQuantity() < request.getQuantity()) {
      log.warn(
          "[{}] 보유 수량 부족 holding={} requested={}",
          MDC.get("traceId"),
          holding.getHoldingQuantity(),
          request.getQuantity());
      throw new GlobalException(ErrorCode.ORDER_002);
    }
  }

  private BigDecimal getCurrentPrice(String stockCode) {
    return stockPriceHistoryRepository
        .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
        .map(StockPriceHistory::getClosePrice)
        .orElseThrow(
            () -> {
              log.warn("[{}] 현재가 없음 stockCode={}", MDC.get("traceId"), stockCode);
              return new GlobalException(ErrorCode.STOCK_002);
            });
  }

  private Page<StockOrder> findOrdersWithFilter(
      Long accountId, OrderStatus status, OrderType orderType, Pageable pageable) {
    if (status != null && orderType != null) {
      return stockOrderRepository.findBySecuritiesAccountIdAndOrderStatusAndOrderType(
          accountId, status, orderType, pageable);
    }
    if (status != null) {
      return stockOrderRepository.findBySecuritiesAccountIdAndOrderStatus(
          accountId, status, pageable);
    }
    if (orderType != null) {
      return stockOrderRepository.findBySecuritiesAccountIdAndOrderType(
          accountId, orderType, pageable);
    }
    return stockOrderRepository.findBySecuritiesAccountId(accountId, pageable);
  }
}
