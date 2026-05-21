package com.stock.domain.holding.repository;

import com.stock.domain.holding.entity.StockHolding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHoldingRepository extends JpaRepository<StockHolding, Long> {

  List<StockHolding> findBySecuritiesAccountId(Long securitiesAccountId);

  Optional<StockHolding> findBySecuritiesAccountIdAndStockCode(
      Long securitiesAccountId, String stockCode);
}
