package com.stock.domain.reconciliation.repository;

import com.stock.domain.reconciliation.entity.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {}
