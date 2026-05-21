package com.stock.domain.stock.repository;

import com.stock.domain.stock.entity.StockPriceHistory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

  Optional<StockPriceHistory> findTopByStockCodeOrderByCollectedAtDesc(String stockCode);

  List<StockPriceHistory> findByStockCodeAndCollectedAtBetweenOrderByCollectedAtAsc(
      String stockCode, LocalDateTime from, LocalDateTime to);
}
