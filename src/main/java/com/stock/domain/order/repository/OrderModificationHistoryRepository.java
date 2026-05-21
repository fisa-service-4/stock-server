package com.stock.domain.order.repository;

import com.stock.domain.order.entity.OrderModificationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderModificationHistoryRepository
    extends JpaRepository<OrderModificationHistory, Long> {

  List<OrderModificationHistory> findByStockOrderIdOrderByModifiedAtAsc(Long stockOrderId);
}
