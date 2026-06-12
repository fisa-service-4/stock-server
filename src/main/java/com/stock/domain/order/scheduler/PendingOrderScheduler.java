package com.stock.domain.order.scheduler;

import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.repository.StockOrderRepository;
import com.stock.domain.order.service.OrderService;
import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.service.StockPriceHistoryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderScheduler {

  private static final int BATCH_SIZE = 1000;

  private final StockOrderRepository stockOrderRepository;
  private final StockPriceHistoryService stockPriceHistoryService;
  private final OrderService orderService;

  @Scheduled(fixedDelayString = "${stock.order.match-interval:5000}")
  public void matchPendingOrders() {
    List<StockOrder> pendingOrders =
        stockOrderRepository.findByOrderStatus(
            OrderStatus.REQUESTED, PageRequest.of(0, BATCH_SIZE));

    if (pendingOrders.isEmpty()) {
      return;
    }

    log.info("[PendingOrderScheduler] 미체결 주문 처리 시작 count={}", pendingOrders.size());

    Map<String, List<StockOrder>> grouped =
        pendingOrders.stream().collect(Collectors.groupingBy(StockOrder::getStockCode));

    int processedCount = 0;
    int failCount = 0;

    for (Map.Entry<String, List<StockOrder>> entry : grouped.entrySet()) {
      String stockCode = entry.getKey();
      List<StockOrder> orders = entry.getValue();

      BigDecimal currentPrice;
      try {
        StockPriceResponse priceResponse = stockPriceHistoryService.getCurrentPrice(stockCode);
        currentPrice = priceResponse.getCurrentPrice();
      } catch (Exception e) {
        log.warn("[PendingOrderScheduler] 시세 조회 실패 stockCode={} — 해당 종목 주문 skip", stockCode);
        failCount += orders.size();
        continue;
      }

      for (StockOrder order : orders) {
        try {
          orderService.executePendingOrder(order.getStockOrderId(), currentPrice);
          processedCount++;
        } catch (Exception e) {
          log.error(
              "[PendingOrderScheduler] 주문 처리 실패 orderId={} stockCode={} message={}",
              order.getStockOrderId(),
              stockCode,
              e.getMessage());
          failCount++;
        }
      }
    }

    log.info(
        "[PendingOrderScheduler] 미체결 주문 처리 완료 processed={} fail={}", processedCount, failCount);
  }
}
