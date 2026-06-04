package com.stock.domain.stock.repository;

import com.stock.domain.stock.entity.StockPriceHistory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

  Optional<StockPriceHistory> findTopByStockCodeOrderByCollectedAtDesc(String stockCode);

  Optional<StockPriceHistory>
      findTopByStockCodeAndTradedDateBeforeOrderByTradedDateDescCollectedAtDesc(
          String stockCode, LocalDate date);

  List<StockPriceHistory> findByStockCodeAndCollectedAtBetweenOrderByCollectedAtAsc(
      String stockCode, LocalDateTime from, LocalDateTime to);

  List<StockPriceHistory> findByStockCodeAndTradedDateBetweenOrderByTradedDateAsc(
      String stockCode, LocalDate from, LocalDate to);

  boolean existsByStockCodeAndTradedDate(String stockCode, LocalDate tradedDate);

  @Query(
      "SELECT h FROM StockPriceHistory h WHERE h.priceHistoryId IN ("
          + "  SELECT MAX(h2.priceHistoryId) FROM StockPriceHistory h2"
          + "  WHERE h2.stockCode IN :stockCodes GROUP BY h2.stockCode)")
  List<StockPriceHistory> findLatestByStockCodes(@Param("stockCodes") List<String> stockCodes);
}
