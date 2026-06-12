package com.stock.domain.order.repository;

import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

  Page<StockOrder> findBySecuritiesAccountId(Long securitiesAccountId, Pageable pageable);

  Page<StockOrder> findBySecuritiesAccountIdAndOrderStatus(
      Long securitiesAccountId, OrderStatus orderStatus, Pageable pageable);

  Page<StockOrder> findBySecuritiesAccountIdAndOrderType(
      Long securitiesAccountId, OrderType orderType, Pageable pageable);

  Page<StockOrder> findBySecuritiesAccountIdAndOrderStatusAndOrderType(
      Long securitiesAccountId, OrderStatus orderStatus, OrderType orderType, Pageable pageable);

  List<StockOrder> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);
}
