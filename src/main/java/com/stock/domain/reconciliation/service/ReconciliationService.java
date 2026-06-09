package com.stock.domain.reconciliation.service;

import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.execution.repository.StockExecutionRepository;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.repository.StockOrderRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

  private final StockOrderRepository stockOrderRepository;
  private final StockExecutionRepository stockExecutionRepository;
  private final StockHoldingRepository stockHoldingRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;

  /** 주문-체결 수량 정합성: filledQuantity != SUM(executedQuantity) 또는 filledQuantity > orderQuantity */
  @Transactional(readOnly = true)
  public int checkOrderExecutionConsistency() {
    List<OrderStatus> activeStatuses =
        List.of(OrderStatus.FILLED, OrderStatus.PARTIAL_FILLED, OrderStatus.REQUESTED);
    var mismatches = stockOrderRepository.findOrdersWithExecutionMismatch(activeStatuses);
    if (!mismatches.isEmpty()) {
      log.warn(
          "[정합성] 주문-체결 수량 불일치 {}건 orderId={}",
          mismatches.size(),
          mismatches.stream()
              .map(o -> String.valueOf(o.getStockOrderId()))
              .collect(Collectors.joining(",")));
    }
    return mismatches.size();
  }

  /** 체결-보유 수량 정합성: execution 순수량 집계 vs holding 보유수량 비교 */
  @Transactional(readOnly = true)
  public int checkHoldingConsistency() {
    List<Object[]> rows = stockExecutionRepository.findNetQuantityPerAccountAndStock();
    Map<String, Long> executionNetMap = new HashMap<>();
    for (Object[] row : rows) {
      long accountId = ((Number) row[0]).longValue();
      String stockCode = (String) row[1];
      long netQty = ((Number) row[2]).longValue();
      executionNetMap.put(accountId + ":" + stockCode, netQty);
    }

    Map<String, Integer> holdingMap = new HashMap<>();
    for (StockHolding h : stockHoldingRepository.findAll()) {
      holdingMap.put(h.getSecuritiesAccountId() + ":" + h.getStockCode(), h.getHoldingQuantity());
    }

    int mismatch = 0;
    for (Map.Entry<String, Long> entry : executionNetMap.entrySet()) {
      String key = entry.getKey();
      long execNet = entry.getValue();
      int holdingQty = holdingMap.getOrDefault(key, 0);
      if (execNet != holdingQty) {
        mismatch++;
        log.warn("[정합성] 체결-보유 수량 불일치 key={} execNet={} holdingQty={}", key, execNet, holdingQty);
      }
    }
    for (Map.Entry<String, Integer> entry : holdingMap.entrySet()) {
      if (!executionNetMap.containsKey(entry.getKey()) && entry.getValue() > 0) {
        mismatch++;
        log.warn("[정합성] 체결 기록 없는 보유 종목 key={} holdingQty={}", entry.getKey(), entry.getValue());
      }
    }
    return mismatch;
  }

  /** 주문 상태-수량 정합성: FILLED/PARTIAL_FILLED/REQUESTED 상태와 수량 불일치 */
  @Transactional(readOnly = true)
  public int checkOrderStatusConsistency() {
    var mismatches =
        stockOrderRepository.findOrdersWithStatusInconsistency(
            OrderStatus.FILLED, OrderStatus.PARTIAL_FILLED, OrderStatus.REQUESTED);
    if (!mismatches.isEmpty()) {
      log.warn(
          "[정합성] 주문 상태-수량 불일치 {}건 orderId={}",
          mismatches.size(),
          mismatches.stream()
              .map(o -> String.valueOf(o.getStockOrderId()))
              .collect(Collectors.joining(",")));
    }
    return mismatches.size();
  }

  /** 예수금 음수 검증: cashBalance < 0 계좌 탐지 (원장 손상 수준) */
  @Transactional(readOnly = true)
  public int checkCashBalance() {
    var negativeAccounts = securitiesAccountRepository.findAccountsWithNegativeCash();
    if (!negativeAccounts.isEmpty()) {
      log.error("[정합성] 예수금 음수 계좌 {}건 감지 — 즉시 확인 필요", negativeAccounts.size());
    }
    return negativeAccounts.size();
  }

  /** 보유수량 음수 검증: holdingQuantity < 0 종목 탐지 (원장 손상 수준) */
  @Transactional(readOnly = true)
  public int checkHoldingIntegrity() {
    var negativeHoldings = stockHoldingRepository.findHoldingsWithNegativeQuantity();
    if (!negativeHoldings.isEmpty()) {
      log.error("[정합성] 보유수량 음수 종목 {}건 감지 — 즉시 확인 필요", negativeHoldings.size());
    }
    return negativeHoldings.size();
  }
}
