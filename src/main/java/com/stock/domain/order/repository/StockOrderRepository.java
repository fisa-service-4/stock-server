package com.stock.domain.order.repository;

import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

  Page<StockOrder> findBySecuritiesAccountId(Long securitiesAccountId, Pageable pageable);

  Page<StockOrder> findBySecuritiesAccountIdAndOrderStatus(
      Long securitiesAccountId, OrderStatus orderStatus, Pageable pageable);

  Page<StockOrder> findBySecuritiesAccountIdAndOrderType(
      Long securitiesAccountId, OrderType orderType, Pageable pageable);

  Page<StockOrder> findBySecuritiesAccountIdAndOrderStatusAndOrderType(
      Long securitiesAccountId, OrderStatus orderStatus, OrderType orderType, Pageable pageable);

  List<StockOrder> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);

  /** 주문-체결 수량 정합성 검증: filled != SUM(executed) 또는 filled > quantity */
  @Query(
      """
      SELECT o FROM StockOrder o
      WHERE o.orderStatus IN :statuses
      AND (
          o.filledQuantity > o.orderQuantity
          OR o.filledQuantity <> COALESCE(
              (SELECT SUM(e.executedQuantity) FROM StockExecution e
               WHERE e.stockOrderId = o.stockOrderId),
              0
          )
      )
      """)
  List<StockOrder> findOrdersWithExecutionMismatch(@Param("statuses") List<OrderStatus> statuses);

  /** 주문 상태-수량 정합성 검증 */
  @Query(
      """
      SELECT o FROM StockOrder o
      WHERE (
          (o.orderStatus = :filled
           AND (o.remainingQuantity <> 0 OR o.filledQuantity <> o.orderQuantity))
          OR (o.orderStatus = :partialFilled AND o.remainingQuantity = 0)
          OR (o.orderStatus = :requested AND o.filledQuantity > 0)
      )
      """)
  List<StockOrder> findOrdersWithStatusInconsistency(
      @Param("filled") OrderStatus filled,
      @Param("partialFilled") OrderStatus partialFilled,
      @Param("requested") OrderStatus requested);
}
