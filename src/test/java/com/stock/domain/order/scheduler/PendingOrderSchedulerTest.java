package com.stock.domain.order.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.entity.enums.OrderedBy;
import com.stock.domain.order.repository.StockOrderRepository;
import com.stock.domain.order.service.OrderService;
import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PendingOrderSchedulerTest {

  @Mock private StockOrderRepository stockOrderRepository;
  @Mock private StockPriceHistoryService stockPriceHistoryService;
  @Mock private OrderService orderService;

  @InjectMocks private PendingOrderScheduler scheduler;

  private static final String STOCK_CODE_A = "005930";
  private static final String STOCK_CODE_B = "000660";
  private static final BigDecimal PRICE_A = new BigDecimal("70000");
  private static final BigDecimal PRICE_B = new BigDecimal("200000");

  private StockOrder buildOrder(Long id, String stockCode) {
    StockOrder order =
        StockOrder.builder()
            .securitiesAccountId(1L)
            .stockCode(stockCode)
            .orderType(OrderType.BUY)
            .orderMethod(OrderMethod.LIMIT)
            .orderPrice(new BigDecimal("65000"))
            .orderQuantity(10)
            .orderedBy(OrderedBy.USER)
            .orderedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(order, "stockOrderId", id);
    return order;
  }

  private StockPriceResponse priceResponse(String stockCode, BigDecimal price) {
    return StockPriceResponse.builder()
        .stockCode(stockCode)
        .stockName("테스트종목")
        .currentPrice(price)
        .build();
  }

  @Nested
  @DisplayName("matchPendingOrders — 미체결 주문 처리")
  class MatchPendingOrders {

    @Test
    @DisplayName("REQUESTED 주문이 없으면 가격 조회와 체결 시도 없이 조기 종료된다")
    void noPendingOrders_earlyReturn() {
      when(stockOrderRepository.findByOrderStatus(eq(OrderStatus.REQUESTED), any()))
          .thenReturn(Collections.emptyList());

      scheduler.matchPendingOrders();

      verify(stockPriceHistoryService, never()).getCurrentPrice(any());
      verify(orderService, never()).executePendingOrder(any(), any());
    }

    @Test
    @DisplayName("동일 종목의 주문 2건: 가격 조회 1회, 체결 시도 2회")
    void pendingOrders_sameStock_fetchesPriceOnce_executesTwice() {
      when(stockOrderRepository.findByOrderStatus(eq(OrderStatus.REQUESTED), any()))
          .thenReturn(List.of(buildOrder(1L, STOCK_CODE_A), buildOrder(2L, STOCK_CODE_A)));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE_A))
          .thenReturn(priceResponse(STOCK_CODE_A, PRICE_A));

      scheduler.matchPendingOrders();

      verify(stockPriceHistoryService, times(1)).getCurrentPrice(STOCK_CODE_A);
      verify(orderService).executePendingOrder(1L, PRICE_A);
      verify(orderService).executePendingOrder(2L, PRICE_A);
    }

    @Test
    @DisplayName("다른 두 종목 주문: 종목별로 독립적으로 가격 조회 후 체결한다")
    void pendingOrders_differentStocks_eachFetchedAndExecuted() {
      when(stockOrderRepository.findByOrderStatus(eq(OrderStatus.REQUESTED), any()))
          .thenReturn(List.of(buildOrder(1L, STOCK_CODE_A), buildOrder(2L, STOCK_CODE_B)));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE_A))
          .thenReturn(priceResponse(STOCK_CODE_A, PRICE_A));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE_B))
          .thenReturn(priceResponse(STOCK_CODE_B, PRICE_B));

      scheduler.matchPendingOrders();

      verify(orderService).executePendingOrder(1L, PRICE_A);
      verify(orderService).executePendingOrder(2L, PRICE_B);
    }

    @Test
    @DisplayName("종목 A의 가격 조회 실패 시 A 주문 전체를 skip하고 종목 B 주문은 정상 처리된다")
    void priceServiceThrows_skipsThatStock_continuesOthers() {
      when(stockOrderRepository.findByOrderStatus(eq(OrderStatus.REQUESTED), any()))
          .thenReturn(List.of(buildOrder(1L, STOCK_CODE_A), buildOrder(2L, STOCK_CODE_B)));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE_A))
          .thenThrow(new GlobalException(ErrorCode.STOCK_002));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE_B))
          .thenReturn(priceResponse(STOCK_CODE_B, PRICE_B));

      scheduler.matchPendingOrders();

      verify(orderService, never()).executePendingOrder(eq(1L), any());
      verify(orderService).executePendingOrder(2L, PRICE_B);
    }

    @Test
    @DisplayName("첫 번째 주문 체결 실패 시 예외를 삼키고 두 번째 주문을 계속 처리한다")
    void firstOrderExecutionFails_continuesWithSecondOrder() {
      when(stockOrderRepository.findByOrderStatus(eq(OrderStatus.REQUESTED), any()))
          .thenReturn(List.of(buildOrder(1L, STOCK_CODE_A), buildOrder(2L, STOCK_CODE_A)));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE_A))
          .thenReturn(priceResponse(STOCK_CODE_A, PRICE_A));
      doThrow(new RuntimeException("체결 실패")).when(orderService).executePendingOrder(1L, PRICE_A);

      scheduler.matchPendingOrders();

      verify(orderService).executePendingOrder(1L, PRICE_A);
      verify(orderService).executePendingOrder(2L, PRICE_A);
    }
  }
}
