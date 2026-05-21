package com.stock.domain.stock.repository;

import com.stock.domain.stock.entity.StockMaster;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMasterRepository extends JpaRepository<StockMaster, String> {

  List<StockMaster> findByStockNameContainingIgnoreCaseOrStockCodeContainingIgnoreCase(
      String stockName, String stockCode);
}
