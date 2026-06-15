package com.stock.domain.execution.repository;

import com.stock.domain.execution.entity.StockExecution;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockExecutionRepository extends JpaRepository<StockExecution, Long> {

  Page<StockExecution> findByStockOrderId(Long stockOrderId, Pageable pageable);

  Page<StockExecution> findByStockCodeAndExecutedAtBetween(
      String stockCode, LocalDateTime from, LocalDateTime to, Pageable pageable);

  Page<StockExecution> findByExecutedAtBetween(
      LocalDateTime from, LocalDateTime to, Pageable pageable);

  @Query(
      "SELECT e FROM StockExecution e"
          + " WHERE e.stockOrderId IN"
          + "   (SELECT o.stockOrderId FROM StockOrder o WHERE o.securitiesAccountId = :accountId)"
          + " AND (:stockCode IS NULL OR e.stockCode = :stockCode)"
          + " AND (:from IS NULL OR e.executedAt >= :from)"
          + " AND (:to IS NULL OR e.executedAt <= :to)")
  Page<StockExecution> findByAccountIdWithFilters(
      @Param("accountId") Long accountId,
      @Param("stockCode") String stockCode,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);

  /**
   * 체결-보유 정합성 검증: (account, stock) 단위 BUY/SELL 순수량 집계 row: [securities_account_id, stock_code,
   * net_qty]
   */
  @Query(
      value =
          """
          SELECT o.securities_account_id, e.stock_code,
                 SUM(CASE WHEN o.order_type = 'BUY' THEN e.executed_quantity
                          ELSE -e.executed_quantity END)
          FROM stock_execution e
          JOIN stock_order o ON e.stock_order_id = o.stock_order_id
          GROUP BY o.securities_account_id, e.stock_code
          """,
      nativeQuery = true)
  List<Object[]> findNetQuantityPerAccountAndStock();
}
