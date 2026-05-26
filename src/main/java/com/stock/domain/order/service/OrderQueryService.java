package com.stock.domain.order.service;

import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.order.dto.response.OrderDetailResponse;
import com.stock.domain.order.dto.response.OrderListItemResponse;
import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.repository.StockOrderRepository;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

  private final StockOrderRepository stockOrderRepository;
  private final StockMasterRepository stockMasterRepository;
  private final AccountValidator accountValidator;

  @Transactional(readOnly = true)
  public Page<OrderListItemResponse> getOrders(
      Long userId, Long accountId, OrderStatus status, OrderType orderType, int page, int size) {

    accountValidator.validateOwner(userId, accountId);

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt"));
    Page<StockOrder> orders;

    if (status != null && orderType != null) {
      orders =
          stockOrderRepository.findBySecuritiesAccountIdAndOrderStatusAndOrderType(
              accountId, status, orderType, pageable);
    } else if (status != null) {
      orders =
          stockOrderRepository.findBySecuritiesAccountIdAndOrderStatus(
              accountId, status, pageable);
    } else if (orderType != null) {
      orders =
          stockOrderRepository.findBySecuritiesAccountIdAndOrderType(
              accountId, orderType, pageable);
    } else {
      orders = stockOrderRepository.findBySecuritiesAccountId(accountId, pageable);
    }

    Map<String, String> nameCache = new HashMap<>();
    log.info(
        "[{}] 주문 목록 조회 accountId={} total={}",
        MDC.get("traceId"),
        accountId,
        orders.getTotalElements());
    return orders.map(
        order -> {
          String name =
              nameCache.computeIfAbsent(
                  order.getStockCode(),
                  code ->
                      stockMasterRepository
                          .findById(code)
                          .map(StockMaster::getStockName)
                          .orElse(code));
          return OrderListItemResponse.from(order, name);
        });
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
            .orElse(order.getStockCode());

    log.info("[{}] 주문 상세 조회 orderId={}", MDC.get("traceId"), orderId);
    return OrderDetailResponse.from(order, stockName);
  }
}
