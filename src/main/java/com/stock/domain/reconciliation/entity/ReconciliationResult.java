package com.stock.domain.reconciliation.entity;

import com.stock.domain.reconciliation.entity.enums.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reconciliation_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "run_date", nullable = false)
  private LocalDate runDate;

  @Column(name = "order_mismatch_count", nullable = false)
  private int orderMismatchCount;

  @Column(name = "holding_mismatch_count", nullable = false)
  private int holdingMismatchCount;

  @Column(name = "status_mismatch_count", nullable = false)
  private int statusMismatchCount;

  @Column(name = "cash_mismatch_count", nullable = false)
  private int cashMismatchCount;

  @Column(name = "holding_integrity_count", nullable = false)
  private int holdingIntegrityCount;

  @Column(name = "total_mismatch_count", nullable = false)
  private int totalMismatchCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 10, nullable = false)
  private ReconciliationStatus status;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void prePersist() {
    this.createdAt = LocalDateTime.now();
  }

  @Builder
  public ReconciliationResult(
      LocalDate runDate,
      int orderMismatchCount,
      int holdingMismatchCount,
      int statusMismatchCount,
      int cashMismatchCount,
      int holdingIntegrityCount,
      int totalMismatchCount,
      ReconciliationStatus status,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      Long durationMs) {
    this.runDate = runDate;
    this.orderMismatchCount = orderMismatchCount;
    this.holdingMismatchCount = holdingMismatchCount;
    this.statusMismatchCount = statusMismatchCount;
    this.cashMismatchCount = cashMismatchCount;
    this.holdingIntegrityCount = holdingIntegrityCount;
    this.totalMismatchCount = totalMismatchCount;
    this.status = status;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.durationMs = durationMs;
  }
}
