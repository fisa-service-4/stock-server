package com.stock.domain.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.execution.entity.StockExecution;
import com.stock.domain.execution.repository.StockExecutionRepository;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.order.dto.request.OrderCreateRequest;
import com.stock.domain.order.dto.response.OrderCancelResponse;
import com.stock.domain.order.dto.response.OrderCreateResponse;
import com.stock.domain.order.entity.OrderModificationHistory;
import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.ModificationType;
import com.stock.domain.order.entity.enums.ModifiedBy;
import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.entity.enums.OrderedBy;
import com.stock.domain.order.repository.OrderModificationHistoryRepository;
import com.stock.domain.order.repository.StockOrderRepository;
import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

  private static final Set<OrderStatus> CANCELLABLE_STATUSES = EnumSet.of(OrderStatus.REQUESTED);

  private final StockOrderRepository stockOrderRepository;
  private final OrderModificationHistoryRepository orderModificationHistoryRepository;
  private final StockExecutionRepository stockExecutionRepository;
  private final StockHoldingRepository stockHoldingRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final StockMasterRepository stockMasterRepository;
  private final AccountValidator accountValidator;
  private final StockPriceHistoryService stockPriceHistoryService;
  private final ObjectMapper objectMapper;

  @Transactional
  public OrderCreateResponse createOrder(Long userId, Long accountId, OrderCreateRequest request) {

    SecuritiesAccount account = accountValidator.validateOwner(userId, accountId);

    stockMasterRepository
        .findById(request.getStockCode())
        .orElseThrow(
            () -> {
              log.warn("[{}] 종목 없음 stockCode={}", MDC.get("traceId"), request.getStockCode());
              return new GlobalException(ErrorCode.STOCK_001);
            });

    StockPriceResponse priceResponse =
        stockPriceHistoryService.getCurrentPrice(request.getStockCode());
    BigDecimal currentPrice = priceResponse.getCurrentPrice();

    validateOrderCondition(request, account, currentPrice);

    StockOrder order =
        StockOrder.builder()
            .securitiesAccountId(accountId)
            .stockCode(request.getStockCode())
            .orderType(request.getOrderType())
            .orderMethod(request.getOrderMethod())
            .orderPrice(request.getPrice())
            .orderQuantity(request.getQuantity())
            .orderedBy(OrderedBy.USER)
            .orderedAt(LocalDateTime.now())
            .build();

    stockOrderRepository.save(order);
    log.info(
        "[{}] 주문 생성 orderId={} stockCode={} orderType={} method={}",
        MDC.get("traceId"),
        order.getStockOrderId(),
        order.getStockCode(),
        order.getOrderType(),
        order.getOrderMethod());

    tryExecute(order, account, currentPrice);

    return OrderCreateResponse.from(order);
  }

  @Transactional
  public OrderCancelResponse cancelOrder(Long userId, Long orderId) {
    StockOrder order =
        stockOrderRepository
            .findById(orderId)
            .orElseThrow(
                () -> {
                  log.warn("[{}] 주문 없음 orderId={}", MDC.get("traceId"), orderId);
                  return new GlobalException(ErrorCode.ORDER_003);
                });

    if (!CANCELLABLE_STATUSES.contains(order.getOrderStatus())) {
      log.warn(
          "[{}] 취소 불가 상태 orderId={} status={}",
          MDC.get("traceId"),
          orderId,
          order.getOrderStatus());
      throw new GlobalException(ErrorCode.ORDER_004);
    }

    accountValidator.validateOwner(userId, order.getSecuritiesAccountId());

    int cancelledQuantity = order.getRemainingQuantity();

    String beforePayload = serializeOrder(order);
    order.cancel();
    String afterPayload = serializeOrder(order);

    orderModificationHistoryRepository.save(
        OrderModificationHistory.builder()
            .stockOrderId(orderId)
            .modificationType(ModificationType.CANCEL)
            .beforePayload(beforePayload)
            .afterPayload(afterPayload)
            .modifiedBy(ModifiedBy.USER)
            .modifiedAt(LocalDateTime.now())
            .build());

    log.info("[{}] 주문 취소 완료 orderId={}", MDC.get("traceId"), orderId);
    return OrderCancelResponse.from(order, cancelledQuantity);
  }

  // ──────────────────────────────────────────────────────────
  // private
  // ──────────────────────────────────────────────────────────

  private void validateOrderCondition(
      OrderCreateRequest request, SecuritiesAccount account, BigDecimal currentPrice) {
    if (request.getOrderType() == OrderType.BUY) {
      BigDecimal basePrice =
          request.getOrderMethod() == OrderMethod.MARKET ? currentPrice : request.getPrice();
      BigDecimal required = basePrice.multiply(BigDecimal.valueOf(request.getQuantity()));
      if (account.getCashBalance().compareTo(required) < 0) {
        log.warn(
            "[{}] 주문 가능 금액 부족 cashBalance={} required={}",
            MDC.get("traceId"),
            account.getCashBalance(),
            required);
        throw new GlobalException(ErrorCode.ORDER_001);
      }
    } else {
      StockHolding holding =
          stockHoldingRepository
              .findBySecuritiesAccountIdAndStockCode(
                  account.getSecuritiesAccountId(), request.getStockCode())
              .orElseThrow(
                  () -> {
                    log.warn(
                        "[{}] 보유 종목 없음 stockCode={}", MDC.get("traceId"), request.getStockCode());
                    return new GlobalException(ErrorCode.ORDER_002);
                  });
      if (holding.getHoldingQuantity() < request.getQuantity()) {
        log.warn(
            "[{}] 보유 수량 부족 holdingQty={} orderQty={}",
            MDC.get("traceId"),
            holding.getHoldingQuantity(),
            request.getQuantity());
        throw new GlobalException(ErrorCode.ORDER_002);
      }
    }
  }

  private void tryExecute(StockOrder order, SecuritiesAccount account, BigDecimal currentPrice) {
    boolean shouldExecute = isExecutable(order, currentPrice);
    if (!shouldExecute) {
      log.info(
          "[{}] LIMIT 조건 미충족, 주문 대기 orderId={} orderPrice={} currentPrice={}",
          MDC.get("traceId"),
          order.getStockOrderId(),
          order.getOrderPrice(),
          currentPrice);
      return;
    }

    try {
      BigDecimal executionAmount =
          currentPrice.multiply(BigDecimal.valueOf(order.getOrderQuantity()));

      StockExecution execution =
          StockExecution.builder()
              .stockOrderId(order.getStockOrderId())
              .stockCode(order.getStockCode())
              .executedPrice(currentPrice)
              .executedQuantity(order.getOrderQuantity())
              .executionAmount(executionAmount)
              .executedAt(LocalDateTime.now())
              .build();
      stockExecutionRepository.save(execution);

      applyHolding(order, currentPrice, executionAmount, account);

      if (order.getOrderType() == OrderType.BUY) {
        account.withdraw(executionAmount);
      } else {
        account.deposit(executionAmount);
      }
      securitiesAccountRepository.save(account);

      order.fill(order.getOrderQuantity(), currentPrice);

      log.info(
          "[{}] 체결 완료 orderId={} executionId={} price={} qty={}",
          MDC.get("traceId"),
          order.getStockOrderId(),
          execution.getExecutionId(),
          currentPrice,
          order.getOrderQuantity());

    } catch (GlobalException e) {
      throw e;
    } catch (Exception e) {
      log.error("[{}] 체결 엔진 오류 orderId={}", MDC.get("traceId"), order.getStockOrderId(), e);
      throw new GlobalException(ErrorCode.ORDER_005);
    }
  }

  private boolean isExecutable(StockOrder order, BigDecimal currentPrice) {
    if (order.getOrderMethod() == OrderMethod.MARKET) {
      return true;
    }
    // LIMIT
    if (order.getOrderType() == OrderType.BUY) {
      return currentPrice.compareTo(order.getOrderPrice()) <= 0;
    } else {
      return currentPrice.compareTo(order.getOrderPrice()) >= 0;
    }
  }

  private void applyHolding(
      StockOrder order,
      BigDecimal executionPrice,
      BigDecimal executionAmount,
      SecuritiesAccount account) {
    var holdingOpt =
        stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
            account.getSecuritiesAccountId(), order.getStockCode());

    if (order.getOrderType() == OrderType.BUY) {
      if (holdingOpt.isPresent()) {
        holdingOpt.get().buy(order.getOrderQuantity(), executionPrice);
        stockHoldingRepository.save(holdingOpt.get());
      } else {
        StockHolding newHolding =
            StockHolding.builder()
                .securitiesAccountId(account.getSecuritiesAccountId())
                .stockCode(order.getStockCode())
                .holdingQuantity(order.getOrderQuantity())
                .averagePurchasePrice(executionPrice)
                .totalPurchaseAmount(executionAmount)
                .build();
        stockHoldingRepository.save(newHolding);
      }
    } else {
      StockHolding holding = holdingOpt.orElseThrow(() -> new GlobalException(ErrorCode.ORDER_002));
      holding.sell(order.getOrderQuantity());
      if (holding.getHoldingQuantity() == 0) {
        stockHoldingRepository.delete(holding);
      } else {
        stockHoldingRepository.save(holding);
      }
    }
  }

  private String serializeOrder(StockOrder order) {
    try {
      return objectMapper.writeValueAsString(order);
    } catch (JsonProcessingException e) {
      log.warn("[{}] 주문 직렬화 실패 orderId={}", MDC.get("traceId"), order.getStockOrderId());
      return "{}";
    }
  }
}
