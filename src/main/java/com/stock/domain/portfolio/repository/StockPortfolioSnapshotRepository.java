package com.stock.domain.portfolio.repository;

import com.stock.domain.portfolio.entity.StockPortfolioSnapshot;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockPortfolioSnapshotRepository
    extends JpaRepository<StockPortfolioSnapshot, Long> {

  Optional<StockPortfolioSnapshot> findTopByUserIdOrderBySnapshotDateDesc(Long userId);

  Optional<StockPortfolioSnapshot> findByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);
}
