package com.stock.domain.execution.repository;

import com.stock.domain.execution.entity.StockExecution;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockExecutionRepository extends JpaRepository<StockExecution, Long> {

  Page<StockExecution> findByStockOrderId(Long stockOrderId, Pageable pageable);

  Page<StockExecution> findByStockCodeAndExecutedAtBetween(
      String stockCode, LocalDateTime from, LocalDateTime to, Pageable pageable);

  Page<StockExecution> findByExecutedAtBetween(
      LocalDateTime from, LocalDateTime to, Pageable pageable);
}
